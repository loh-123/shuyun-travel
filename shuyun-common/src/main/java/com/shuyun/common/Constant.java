package com.shuyun.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 全局常量
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Constant {

    /** 请求头中携带 Token 的字段名 */
    public static final String TOKEN_HEADER = "Authorization";

    /** Token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** Redis 中 Token 的 key 前缀 */
    public static final String REDIS_TOKEN_PREFIX = "shuyun:token:";

    /** Redis 中用户信息的 key 前缀 */
    public static final String REDIS_USER_PREFIX = "shuyun:user:";
}
