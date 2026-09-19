package com.shuyun.web.controller;

import com.shuyun.common.BusinessException;
import com.shuyun.common.Result;
import com.shuyun.pojo.entity.UserAiChat;
import com.shuyun.service.AiChatService;
import com.shuyun.web.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 对话接口
 *
 * <p>当前为占位实现，后续接入女娲智能体的 SSE 流式接口。</p>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    /** SSE 每片推送的字符数 */
    private static final int CHUNK_SIZE = 2;

    /** SSE 每片之间的间隔（毫秒） */
    private static final long STREAM_INTERVAL_MS = 40L;

    private final AiChatService aiChatService;

    /**
     * 发起一次对话
     *
     * @param body 请求体：{message, sessionId}；sessionId 为空时自动生成
     * @return 统一结果，data = {reply, sessionId}
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (!StringUtils.hasText(message)) {
            throw new BusinessException("消息内容不能为空");
        }

        String sessionId = body.get("sessionId");
        if (!StringUtils.hasText(sessionId)) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
        }

        String reply = aiChatService.chat(UserContext.getUserId(), message, sessionId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reply", reply);
        data.put("sessionId", sessionId);
        return Result.ok(data);
    }

    /**
     * 查询会话历史
     *
     * @param sessionId 会话ID
     * @return 该会话的全部对话记录
     */
    @GetMapping("/history")
    public Result<List<UserAiChat>> history(@RequestParam String sessionId) {
        return Result.ok(aiChatService.listBySession(sessionId));
    }

    /**
     * 流式对话（SSE 占位版）
     *
     * <p>当前逐字推送占位文案，后续替换为女娲智能体的真实流式输出。
     * 注意：浏览器 EventSource 无法自定义请求头，因此 token 通过查询参数传递，
     * 由 {@code JwtInterceptor} 统一解析。</p>
     *
     * @param message   用户消息
     * @param sessionId 会话ID，为空时自动生成
     * @return SSE 事件流
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String message,
                                 @RequestParam(required = false) String sessionId) {
        if (!StringUtils.hasText(message)) {
            throw new BusinessException("消息内容不能为空");
        }

        String sid = StringUtils.hasText(sessionId)
                ? sessionId
                : UUID.randomUUID().toString().replace("-", "");
        Long userId = UserContext.getUserId();

        SseEmitter emitter = new SseEmitter(60_000L);
        try {
            // 1) 保存用户消息 + 占位回复（与同步接口一致）
            String reply = aiChatService.chat(userId, message, sid);

            // 2) 先把 sessionId 推给前端
            emitter.send(SseEmitter.event().name("session").data(sid));

            // 3) 逐字推送回复（占位实现，无真实模型调用）
            for (int i = CHUNK_SIZE; i < reply.length() + CHUNK_SIZE; i += CHUNK_SIZE) {
                String chunk = reply.substring(0, Math.min(i, reply.length()));
                emitter.send(SseEmitter.event().name("message").data(chunk));
                Thread.sleep(STREAM_INTERVAL_MS);
            }

            emitter.send(SseEmitter.event().name("done").data("done"));
            emitter.complete();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.completeWithError(e);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }
}
