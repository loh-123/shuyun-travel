package com.shuyun.web.interceptor;

import com.shuyun.common.BusinessException;
import com.shuyun.pojo.entity.User;
import com.shuyun.service.UserService;
import com.shuyun.web.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 后台管理员权限拦截器
 *
 * <p>在 {@code JwtInterceptor} 校验 token 并写入 {@link UserContext} 之后执行，
 * 进一步校验当前登录用户的角色是否为 {@code ADMIN}，非管理员拒绝访问 /api/admin/**。</p>
 */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    /** 管理员角色标识 */
    private static final String ROLE_ADMIN = "ADMIN";

    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行 CORS 预检请求：OPTIONS 不带登录态，否则会被判为 403
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Long userId = UserContext.getUserId();
        User user = userId == null ? null : userService.getById(userId);
        if (user == null || !ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException(403, "无后台访问权限，请使用管理员账号登录");
        }
        return true;
    }
}
