package com.shuyun.web.context;

/**
 * 当前登录用户上下文
 *
 * <p>基于 {@link ThreadLocal} 保存当前请求的登录用户ID，
 * 由 {@code JwtInterceptor} 写入、请求结束后清理。</p>
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 设置当前登录用户ID
     *
     * @param userId 用户ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取当前登录用户ID
     *
     * @return 用户ID，未登录返回 null
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 清理当前线程的登录用户ID
     */
    public static void clear() {
        USER_ID.remove();
    }
}
