package com.shuyun.web.controller;

import com.shuyun.common.PageResult;
import com.shuyun.common.Result;
import com.shuyun.pojo.vo.FeedbackVO;
import com.shuyun.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 问题反馈接口（管理端，仅管理员）
 *
 * <p>权限校验见 {@code AdminInterceptor}。</p>
 */
@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 全部反馈列表（分页）
     *
     * @param status  状态筛选：0-待处理，1-已处理；不传为全部
     * @param keyword 内容关键词（可选）
     * @param page    页码，默认 1
     * @param size    每页大小，默认 10
     * @return 分页结果（含提交人用户名）
     */
    @GetMapping
    public Result<PageResult<FeedbackVO>> list(@RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") Integer page,
                                               @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(feedbackService.pageAll(status, keyword, page, size));
    }

    /**
     * 处理反馈（标记状态 / 填写回复）
     *
     * @param id   反馈ID
     * @param body 请求体：{status, reply}
     * @return 空结果
     */
    @PutMapping("/{id}")
    public Result<Void> handle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object status = body.get("status");
        Object reply = body.get("reply");
        feedbackService.handle(
                id,
                status == null ? null : Integer.valueOf(status.toString()),
                reply == null ? null : reply.toString());
        return Result.ok();
    }
}
