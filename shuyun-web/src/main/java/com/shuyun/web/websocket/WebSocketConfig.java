package com.shuyun.web.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 *
 * <p>注册数据大屏推送端点 {@code /ws/screen}；该端点不在 {@code /api/**} 下，
 * 因此不受 JWT 拦截器影响（与数据大屏 REST 接口一致，免登录）。</p>
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ScreenWebSocket screenWebSocket;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(screenWebSocket, "/ws/screen")
                .setAllowedOriginPatterns("*");
    }
}
