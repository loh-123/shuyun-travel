package com.shuyun.web.controller;

import com.shuyun.common.JwtUtil;
import com.shuyun.common.Result;
import com.shuyun.pojo.entity.User;
import com.shuyun.service.UserService;
import com.shuyun.web.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    private final JwtUtil jwtUtil;

    /**
     * 用户注册
     *
     * @param body 请求体：{username, email, password}
     * @return 统一结果
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody Map<String, String> body) {
        userService.register(body.get("username"), body.get("email"), body.get("password"));
        return Result.ok();
    }

    /**
     * 用户登录，支持用户名或邮箱
     *
     * @param body 请求体：{account, password}
     * @return 统一结果，data = {token, user}
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String token = userService.login(body.get("account"), body.get("password"));
        User user = userService.getCurrentUser(jwtUtil.getUserId(token));
        user.setPassword(null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("user", user);
        return Result.ok(data);
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 统一结果，data = 当前用户
     */
    @GetMapping("/me")
    public Result<User> me() {
        User user = userService.getCurrentUser(UserContext.getUserId());
        user.setPassword(null);
        return Result.ok(user);
    }
}
