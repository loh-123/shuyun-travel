package com.shuyun.web.controller;

import com.shuyun.common.RedisUtil;
import com.shuyun.common.Result;
import com.shuyun.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 邮箱验证码接口
 *
 * <p>提供「发送」与「校验」两个接口；验证码存于 Redis（50 秒有效），
 * 注册时由服务端再次校验。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class EmailController {

    /** 邮箱格式校验 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final EmailService emailService;

    private final RedisUtil redisUtil;

    /**
     * 发送邮箱验证码
     *
     * @param body 请求体：{email}
     * @return 统一结果，data = {message}
     */
    @PostMapping("/send-email-code")
    public Result<Map<String, Object>> sendEmailCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        // 1. 校验邮箱格式
        if (!StringUtils.hasText(email) || !EMAIL_PATTERN.matcher(email).matches()) {
            return Result.fail("邮箱格式不正确");
        }
        // 2. 防止重复发送（Redis 中仍有未过期验证码时拒绝）
        if (Boolean.TRUE.equals(redisUtil.hasKey(EmailService.CODE_KEY_PREFIX + email))) {
            return Result.fail("验证码已发送，请 50 秒后再试");
        }
        // 3. 调用邮件业务发送验证码
        try {
            emailService.sendVerificationCode(email);
        } catch (Exception e) {
            log.error("邮箱验证码发送失败：email={}", email, e);
            return Result.fail("验证码发送失败，请稍后重试");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("message", "验证码已发送");
        return Result.ok(data);
    }

    /**
     * 校验邮箱验证码
     *
     * @param body 请求体：{email, code}
     * @return 统一结果
     */
    @PostMapping("/verify-email-code")
    public Result<Void> verifyEmailCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        // 1. 取 Redis 中的验证码
        String cached = redisUtil.get(EmailService.CODE_KEY_PREFIX + email);
        if (cached == null) {
            return Result.fail("验证码不存在或已过期");
        }
        // 2. 比对是否一致
        if (!cached.equals(code)) {
            return Result.fail("验证码错误");
        }
        // 3. 校验通过后删除，避免重复使用
        redisUtil.delete(EmailService.CODE_KEY_PREFIX + email);
        return Result.ok();
    }
}
