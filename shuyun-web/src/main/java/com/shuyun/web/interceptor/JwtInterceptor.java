package com.shuyun.web.interceptor;

import com.shuyun.common.BusinessException;
import com.shuyun.common.Constant;
import com.shuyun.common.JwtUtil;
import com.shuyun.web.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 登录拦截器
 *
 * <p>从请求头 {@code Authorization} 解析 Token，校验通过后把用户ID写入 {@link UserContext}。
 * 拦截路径与放行路径在 {@code WebMvcConfig} 中配置（/api/** 放行登录、注册、健康检查）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行 CORS 预检请求：OPTIONS 不携带 Authorization，否则会被判为 401
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(401, "未登录，请先登录");
        }
        if (!jwtUtil.validateToken(token)) {
            throw new BusinessException(401, "Token 无效或已过期，请重新登录");
        }

        UserContext.setUserId(jwtUtil.getUserId(token));
        return true;
    }

    /**
     * 解析 token：优先取 Authorization 请求头；
     * SSE（EventSource）无法自定义请求头，因此兼容 {@code ?token=xxx}
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(Constant.TOKEN_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(Constant.TOKEN_PREFIX)) {
            return header.substring(Constant.TOKEN_PREFIX.length());
        }
        return request.getParameter("token");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
