package com.shuyun.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 大屏实时推送服务（SSE）
 *
 * <p>基于 {@link SseEmitter} 实现服务端推送：每 5 秒向所有已连接的大屏客户端广播
 * 一次实时数据（最近 10 条用户行为，字段与 {@code /api/screen/real-time} 完全一致）。</p>
 *
 * <p>说明：项目未引入 {@code spring-websocket} 依赖，这里用 Spring MVC 原生 SSE 承担
 * 实时推送（与 AI 流式对话同源）。若后续需要 WebSocket，只需保留本类的广播调度逻辑，
 * 将其替换为 {@code TextWebSocketHandler} 即可。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenStreamService {

    /** 推送间隔（秒） */
    private static final long PUSH_INTERVAL = 5L;

    /** 单次推送条数 */
    private static final int PUSH_LIMIT = 10;

    private final ScreenService screenService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 在线客户端 */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService scheduler;

    /** 启动定时广播 */
    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "screen-sse-broadcast");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::broadcast, PUSH_INTERVAL, PUSH_INTERVAL, TimeUnit.SECONDS);
        log.info("大屏 SSE 推送已启动，间隔 {} 秒", PUSH_INTERVAL);
    }

    /** 关闭线程池 */
    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 建立订阅
     *
     * @return SSE 连接
     */
    public SseEmitter subscribe() {
        // 0L 表示不主动超时
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        // 立即推一次，避免首屏空白
        try {
            send(emitter, screenService.realTime(PUSH_LIMIT));
        } catch (Exception e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** 向所有客户端广播 */
    private void broadcast() {
        if (emitters.isEmpty()) {
            return;
        }
        List<Map<String, Object>> data;
        try {
            data = screenService.realTime(PUSH_LIMIT);
        } catch (Exception e) {
            log.warn("大屏实时数据查询失败，跳过本次推送");
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                send(emitter, data);
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    /** 发送一条命名事件 */
    private void send(SseEmitter emitter, Object payload) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        emitter.send(SseEmitter.event().name("realtime").data(json));
    }
}
