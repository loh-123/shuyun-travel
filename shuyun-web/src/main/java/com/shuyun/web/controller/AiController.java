package com.shuyun.web.controller;

import com.shuyun.ai.nuwa.NuwaAgentService;
import com.shuyun.common.BusinessException;
import com.shuyun.common.Result;
import com.shuyun.pojo.entity.UserAiChat;
import com.shuyun.service.AiChatService;
import com.shuyun.web.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 对话接口（女娲智能体）
 *
 * <p>对话经 {@link NuwaAgentService} 转发至女娲平台智能体，以 SSE 流式返回：
 * 每段正文为 {@code data: {"content":"xxx"}}，结束时为 {@code data: [DONE]}。
 * 会话ID通过响应头 {@code X-Session-Id} 回传。</p>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    /** 响应头：回传本次会话ID（前端为空时用于同步） */
    private static final String HEADER_SESSION_ID = "X-Session-Id";

    private final AiChatService aiChatService;

    private final NuwaAgentService nuwaAgentService;

    /**
     * 发起一次对话（SSE 流式）
     *
     * <p>请求体：{message, sessionId}；sessionId 为空时自动生成。
     * token 通过请求头 {@code Authorization} 携带，由 {@code JwtInterceptor} 解析。</p>
     *
     * @param body 请求体
     * @return SSE 事件流
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (!StringUtils.hasText(message)) {
            throw new BusinessException("消息内容不能为空");
        }

        String sessionId = body.get("sessionId");
        if (!StringUtils.hasText(sessionId)) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
        }

        SseEmitter emitter = nuwaAgentService.chat(UserContext.getUserId(), message, sessionId);
        return ResponseEntity.ok()
                .header(HEADER_SESSION_ID, sessionId)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    /**
     * 查询会话历史
     *
     * @param sessionId 会话ID
     * @return 该会话的全部对话记录（仅限本人）
     */
    @GetMapping("/history")
    public Result<List<UserAiChat>> history(@RequestParam String sessionId) {
        return Result.ok(aiChatService.listBySession(UserContext.getUserId(), sessionId));
    }

    /**
     * 查询当前用户的会话列表（按最近消息倒序）
     *
     * @return [{sessionId, title, lastTime, messageCount}]
     */
    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> sessions() {
        return Result.ok(aiChatService.listSessions(UserContext.getUserId()));
    }

    /**
     * 查询当前用户的 AI 画像（兴趣 / 同行 / 预算等），供对话页「记忆体」面板展示
     *
     * @return 画像字段（无记录时为空对象）
     */
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        return Result.ok(aiChatService.profile(UserContext.getUserId()));
    }

    /**
     * 删除某个会话的全部消息
     *
     * @param sessionId 会话ID
     * @return 空结果
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> removeSession(@PathVariable String sessionId) {
        aiChatService.removeSession(UserContext.getUserId(), sessionId);
        return Result.ok();
    }
}
