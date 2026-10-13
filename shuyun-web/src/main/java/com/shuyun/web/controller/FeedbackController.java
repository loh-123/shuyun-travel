package com.shuyun.web.controller;

import com.shuyun.common.PageResult;
import com.shuyun.common.Result;
import com.shuyun.pojo.entity.UserFeedback;
import com.shuyun.service.FeedbackService;
import com.shuyun.web.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 问题反馈接口（用户侧，需登录）
 */
@RestController
@RequestMapping("/api/user/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 提交反馈
     *
     * @param body 请求体：{type, content, contact}
     * @return 反馈ID
     */
    @PostMapping
    public Result<Long> submit(@RequestBody Map<String, String> body) {
        return Result.ok(feedbackService.submit(
                UserContext.getUserId(),
                body.get("type"),
                body.get("content"),
                body.get("contact")));
    }

    /**
     * 我的反馈列表（分页）
     *
     * @param page 页码，默认 1
     * @param size 每页大小，默认 10
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<UserFeedback>> mine(@RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(feedbackService.pageMine(UserContext.getUserId(), page, size));
    }
}
