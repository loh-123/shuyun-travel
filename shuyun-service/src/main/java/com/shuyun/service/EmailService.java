package com.shuyun.service;

import com.shuyun.common.RedisUtil;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 邮件业务：发送邮箱验证码
 *
 * <p>验证码为 4 位随机数字，写入 Redis（key = email:code:{邮箱}，有效期 50 秒），
 * 并以 HTML 邮件发送给用户（只展示验证码，不含任何链接）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    /** Redis 中验证码 key 前缀 */
    public static final String CODE_KEY_PREFIX = "email:code:";

    /** 验证码有效期（秒），不超过 50 秒 */
    private static final long CODE_TTL_SECONDS = 50;

    private final JavaMailSender mailSender;

    private final RedisUtil redisUtil;

    /** 发件人地址（默认与 SMTP 登录账号一致） */
    @Value("${shuyun.mail.from:}")
    private String from;

    /**
     * 发送邮箱验证码
     *
     * @param email 收件邮箱
     * @throws Exception 邮件发送失败时抛出
     */
    public void sendVerificationCode(String email) throws Exception {
        // 1. 生成 4 位随机数字验证码（0000-9999）
        String code = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));

        // 2. 写入 Redis，50 秒后自动过期
        redisUtil.set(CODE_KEY_PREFIX + email, code, CODE_TTL_SECONDS, TimeUnit.SECONDS);

        // 3. 发送 HTML 邮件；发送失败则回滚 Redis，避免占用重发窗口
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("【蜀韵·智游】邮箱验证码");
            helper.setText(buildHtml(code), true);
            mailSender.send(message);
        } catch (Exception e) {
            redisUtil.delete(CODE_KEY_PREFIX + email);
            throw e;
        }

        log.info("邮箱验证码已发送：email={}", email);
    }

    /**
     * 构建验证码邮件正文（HTML，仅展示验证码，不含任何链接）
     *
     * @param code 验证码
     * @return HTML 文本
     */
    private String buildHtml(String code) {
        return "<div style=\"font-family: 'Microsoft YaHei', sans-serif; max-width: 500px; margin: 0 auto; padding: 32px; background: #F5F1E8;\">"
                + "<h2 style=\"color: #8C1F28; font-size: 22px;\">蜀韵·智游</h2>"
                + "<p style=\"color: #1A1A1A; font-size: 15px;\">您好，您正在注册蜀韵·智游账号。</p>"
                + "<p style=\"color: #1A1A1A; font-size: 15px;\">您的邮箱验证码为：</p>"
                + "<div style=\"text-align: center; margin: 24px 0;\">"
                + "<span style=\"font-size: 36px; font-weight: 700; color: #8C1F28; letter-spacing: 8px;\">"
                + code
                + "</span>"
                + "</div>"
                + "<p style=\"color: #6B7280; font-size: 13px;\">验证码 50 秒内有效，请勿泄露给他人。</p>"
                + "<p style=\"color: #6B7280; font-size: 13px;\">如非本人操作，请忽略此邮件。</p>"
                + "<hr style=\"border: none; border-top: 1px solid #E0D8C8; margin: 24px 0;\"/>"
                + "<p style=\"color: #9CA3AF; font-size: 12px;\">蜀韵·智游 · AI 懂你的巴蜀旅行管家</p>"
                + "</div>";
    }
}
