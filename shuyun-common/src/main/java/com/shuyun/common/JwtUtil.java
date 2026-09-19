package com.shuyun.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 *
 * <p>基于 jjwt 0.12.x API，提供 Token 的生成、解析与校验能力。</p>
 */
@Slf4j
@Component
public class JwtUtil {

    /** 声明字段：用户ID */
    private static final String CLAIM_USER_ID = "userId";

    /** 声明字段：用户名 */
    private static final String CLAIM_USERNAME = "username";

    /**
     * 签名密钥，读取配置项 shuyun.jwt.secret，
     * 默认值 "shuyun-travel-secret-key-2026-shuyun-travel"
     */
    @Value("${shuyun.jwt.secret:shuyun-travel-secret-key-2026-shuyun-travel}")
    private String secret;

    /** Token 过期时间（毫秒），默认 7 天 */
    @Value("${shuyun.jwt.expire:604800000}")
    private Long expire;

    /**
     * 构建 HMAC 签名密钥
     *
     * @return 签名密钥
     */
    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expire);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey())
                .compact();
    }

    /**
     * 解析 Token
     *
     * @param token JWT 字符串
     * @return 载荷 Claims
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取 Token 中的用户ID
     *
     * @param token JWT 字符串
     * @return 用户ID
     */
    public Long getUserId(String token) {
        Object userId = parseToken(token).get(CLAIM_USER_ID);
        return userId == null ? null : Long.valueOf(userId.toString());
    }

    /**
     * 获取 Token 中的用户名
     *
     * @param token JWT 字符串
     * @return 用户名
     */
    public String getUsername(String token) {
        Object username = parseToken(token).get(CLAIM_USERNAME);
        return username == null ? null : username.toString();
    }

    /**
     * 校验 Token 是否有效
     *
     * @param token JWT 字符串
     * @return true-有效，false-无效或已过期
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            log.warn("JWT 校验失败：{}", e.getMessage());
            return false;
        }
    }
}
