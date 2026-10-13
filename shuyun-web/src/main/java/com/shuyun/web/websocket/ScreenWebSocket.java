package com.shuyun.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuyun.service.ScreenService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 数据大屏 WebSocket 推送（/ws/screen）
 *
 * <p>每 5 秒向所有已连接的大屏客户端推送一次实时数据（最近 10 条用户行为），
 * 字段与 {@code /api/screen/real-time} 完全一致。</p>
 *
 * <p>说明：为兼容不同客户端，SSE（{@code /api/screen/stream}）与 WebSocket 同时保留，
 * 前端默认使用 WebSocket，连接失败时可回退 SSE。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScreenWebSocket extends TextWebSocketHandler {

    /** 推送间隔（秒） */
    private static final long PUSH_INTERVAL = 5L;

    /** 单次推送条数 */
    private static final int PUSH_LIMIT = 10;

    /** 单次发送缓冲上限（字节）/超时（毫秒），超出则关闭该会话 */
    private static final int SEND_BUFFER_LIMIT = 512 * 1024;
    private static final int SEND_TIME_LIMIT = 10_000;

    private final ScreenService screenService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 在线会话（使用并发装饰器保证同一会话串行发送） */
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    private ScheduledExecutorService scheduler;

    /** 启动定时广播 */
    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "screen-ws-broadcast");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::broadcast, PUSH_INTERVAL, PUSH_INTERVAL, TimeUnit.SECONDS);
        log.info("大屏 WebSocket 推送已启动：/ws/screen，间隔 {} 秒", PUSH_INTERVAL);
    }

    /** 关闭线程池 */
    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        sessions.forEach(this::closeQuietly);
        sessions.clear();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT, SEND_BUFFER_LIMIT);
        sessions.add(safe);
        // 建立连接后立即推一次，避免首屏空白
        try {
            send(safe, screenService.realTime(PUSH_LIMIT));
        } catch (Exception e) {
            removeQuietly(safe);
        }
        log.info("大屏 WebSocket 连接建立，当前在线 {} 个", sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.removeIf(s -> s.getId().equals(session.getId()));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        removeQuietly(session);
    }

    /** 向所有在线会话广播 */
    private void broadcast() {
        if (sessions.isEmpty()) {
            return;
        }
        List<Map<String, Object>> data;
        try {
            data = screenService.realTime(PUSH_LIMIT);
        } catch (Exception e) {
            log.warn("大屏实时数据查询失败，跳过本次推送");
            return;
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                send(session, data);
            } catch (Exception e) {
                removeQuietly(session);
            }
        }
    }

    /** 发送一条 JSON 文本消息 */
    private void send(WebSocketSession session, Object payload) throws Exception {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private void removeQuietly(WebSocketSession session) {
        sessions.remove(session);
        closeQuietly(session);
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (Exception ignored) {
            // 忽略关闭异常
        }
    }
}
