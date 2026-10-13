package com.shuyun.ai.nuwa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuyun.common.RedisUtil;
import com.shuyun.pojo.entity.UserAiChat;
import com.shuyun.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 女娲智能体业务服务
 *
 * <p>编排「限流校验 → 用户消息落库 → 调用女娲流式接口 → SSE 转发 → AI 回复落库」完整流程，
 * 并记录每次调用的输入长度 / 输出长度 / 耗时 / token / 是否成功。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NuwaAgentService {

    /** 角色：用户 */
    private static final String ROLE_USER = "user";

    /** 角色：AI 助手 */
    private static final String ROLE_ASSISTANT = "assistant";

    /** 流式结束标记 */
    private static final String DONE_FLAG = "[DONE]";

    /** 女娲连接失败且无任何内容时的友好提示 */
    private static final String FALLBACK_REPLY = "抱歉，管家此刻联系不上女娲智能体，请稍后再试～";

    /** 触发重试的兜底文案（与服务端一致，用于判定是否成功） */
    private static final String RETRY_FALLBACK = "管家正在打盹，请稍后再试～";

    /** 限流 key 前缀 */
    private static final String RATE_KEY_PREFIX = "ai:rate:";

    /** 限流时间窗口格式（按分钟） */
    private static final DateTimeFormatter MINUTE_KEY = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final NuwaClient nuwaClient;

    private final NuwaConfig nuwaConfig;

    private final AiChatService aiChatService;

    private final RedisUtil redisUtil;

    private final ObjectMapper objectMapper;

    /**
     * 发起一次流式对话
     *
     * <p>返回的 {@link SseEmitter} 会按 SSE 逐段推送正文，每段为 {@code data: {"content":"xxx"}}，
     * 结束时推送 {@code data: [DONE]}。触发限流时直接推送友好提示。</p>
     *
     * @param userId    用户ID
     * @param message   用户消息
     * @param sessionId 会话ID
     * @return SSE 事件流
     */
    public SseEmitter chat(Long userId, String message, String sessionId) {
        // 超时时间在女娲超时基础上略作放宽，留出落库时间
        SseEmitter emitter = new SseEmitter(nuwaConfig.getTimeout() + 5_000L);

        // 0) 限流：每用户每分钟最多 N 次
        if (!allowRequest(userId)) {
            log.warn("AI 对话触发限流：userId={}", userId);
            sendAndComplete(emitter, "对话有点频繁啦，请休息一下再来找管家吧～（每分钟最多 " + nuwaConfig.getRateLimitPerMinute() + " 次）");
            return emitter;
        }

        AtomicReference<Disposable> subscription = new AtomicReference<>();
        StringBuilder full = new StringBuilder();
        long start = System.currentTimeMillis();
        int inputLen = message == null ? 0 : message.length();
        int[] tokens = {0};

        // 1) 先落库用户消息，保证历史上下文完整
        saveChat(userId, sessionId, ROLE_USER, message);

        // 2) 订阅女娲流式增量并转发给前端
        Disposable disposable = nuwaClient.chatStream(message, sessionId, userId)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        chunk -> onChunk(emitter, subscription, full, tokens, chunk),
                        error -> {
                            log.warn("女娲流式对话失败：userId={}, sessionId={}, err={}",
                                    userId, sessionId, error.getMessage());
                            finalizeStream(emitter, userId, sessionId, full, tokens[0], inputLen, start, false);
                        },
                        () -> finalizeStream(emitter, userId, sessionId, full, tokens[0], inputLen,
                                start, full.length() > 0 && !full.toString().contains(RETRY_FALLBACK)));
        subscription.set(disposable);

        // 3) 客户端断开 / 超时 / 出错时释放上游订阅
        emitter.onCompletion(() -> dispose(subscription));
        emitter.onTimeout(() -> dispose(subscription));
        emitter.onError(e -> dispose(subscription));
        return emitter;
    }

    /**
     * 限流校验：每用户每分钟计数
     *
     * <p>Redis 不可用时放行（不影响可用性）。</p>
     *
     * @param userId 用户ID
     * @return true-允许本次请求
     */
    private boolean allowRequest(Long userId) {
        if (userId == null) {
            return true;
        }
        try {
            String key = RATE_KEY_PREFIX + userId + ":" + MINUTE_KEY.format(LocalDateTime.now());
            Long count = redisUtil.increment(key, 1, TimeUnit.MINUTES);
            return count == null || count <= nuwaConfig.getRateLimitPerMinute();
        } catch (Exception e) {
            log.warn("AI 限流计数失败，放行本次请求：{}", e.getMessage());
            return true;
        }
    }

    /**
     * 处理一个流式分片：累积正文并推送给前端，记录 token
     */
    private void onChunk(SseEmitter emitter, AtomicReference<Disposable> subscription,
                         StringBuilder full, int[] tokens, NuwaChunk chunk) {
        if (chunk.hasTokens()) {
            tokens[0] = chunk.tokens();
        }
        // 思考过程：单独推送 {"reasoning":"..."}，不并入正文，前端按灰色可折叠样式展示
        if (chunk.hasReasoning()) {
            try {
                emitter.send(SseEmitter.event().data(toWireReasoningPayload(chunk.reasoning())));
            } catch (Exception e) {
                dispose(subscription);
            }
            return;
        }
        if (!chunk.hasContent()) {
            return;
        }
        full.append(chunk.content());
        try {
            emitter.send(SseEmitter.event().data(toWirePayload(chunk.content())));
        } catch (Exception e) {
            // 前端已断开，释放上游订阅
            dispose(subscription);
        }
    }

    /**
     * 流结束（正常 / 异常）统一收尾：补发内容、落库（含指标）、推送结束标记
     */
    private void finalizeStream(SseEmitter emitter, Long userId, String sessionId, StringBuilder full,
                                int tokens, int inputLen, long start, boolean success) {
        String reply;
        if (full.length() > 0) {
            reply = full.toString();
        } else {
            // 无任何内容产出（多为连接失败）：以友好提示兜底
            reply = FALLBACK_REPLY;
            try {
                emitter.send(SseEmitter.event().data(toWirePayload(reply)));
            } catch (Exception ignored) {
                // 前端已断开，忽略
            }
        }

        long duration = System.currentTimeMillis() - start;
        // 落库 AI 完整回复 + 调用指标
        aiChatService.saveAiReply(userId, sessionId, reply, tokens, inputLen, reply.length(), duration, success);
        log.info("[AI] userId={}, inputLen={}, outputLen={}, durationMs={}, tokens={}, success={}",
                userId, inputLen, reply.length(), duration, tokens, success);

        try {
            emitter.send(SseEmitter.event().data(DONE_FLAG));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * 直接推送一段内容并结束（用于限流等前置拒绝场景）
     */
    private void sendAndComplete(SseEmitter emitter, String content) {
        try {
            emitter.send(SseEmitter.event().data(toWirePayload(content)));
            emitter.send(SseEmitter.event().data(DONE_FLAG));
            emitter.complete();
        } catch (Exception ignored) {
            emitter.completeWithError(ignored);
        }
    }

    /**
     * 保存一条对话记录
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param role      角色：user / assistant
     * @param content   消息内容
     */
    public void saveChat(Long userId, String sessionId, String role, String content) {
        aiChatService.saveMessage(userId, sessionId, role, content);
    }

    /**
     * 查询某会话的全部对话记录
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 对话记录（按时间正序）
     */
    public List<UserAiChat> getHistory(Long userId, String sessionId) {
        return aiChatService.listBySession(userId, sessionId);
    }

    /**
     * 查询当前用户的会话ID列表
     *
     * @param userId 用户ID
     * @return 会话ID列表（最近消息在前）
     */
    public List<String> getUserSessions(Long userId) {
        return aiChatService.listSessions(userId).stream()
                .map(item -> String.valueOf(item.get("sessionId")))
                .collect(Collectors.toList());
    }

    /**
     * 把一段正文编码为 SSE 数据负载，避免正文中的换行破坏 SSE 分帧
     */
    private String toWirePayload(String content) {
        try {
            Map<String, String> node = new LinkedHashMap<>();
            node.put("content", content);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return content;
        }
    }

    /**
     * 把一段思考过程编码为 SSE 数据负载（{@code {"reasoning":"..."}}）
     */
    private String toWireReasoningPayload(String reasoning) {
        try {
            Map<String, String> node = new LinkedHashMap<>();
            node.put("reasoning", reasoning);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return reasoning;
        }
    }

    /**
     * 释放上游订阅
     */
    private void dispose(AtomicReference<Disposable> subscription) {
        Disposable disposable = subscription.getAndSet(null);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }
}
