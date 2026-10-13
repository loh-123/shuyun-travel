package com.shuyun.web.controller;

import com.shuyun.common.PageResult;
import com.shuyun.common.Result;
import com.shuyun.pojo.entity.EmailSubscription;
import com.shuyun.pojo.vo.AdminNoteVO;
import com.shuyun.pojo.vo.FeedbackVO;
import com.shuyun.service.AdminAuditService;
import com.shuyun.web.util.SimpleExcelWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 内容审核接口（后台，仅管理员）
 *
 * <p>覆盖「用户笔记 / 用户反馈 / 邮箱订阅」，权限校验见 {@code AdminInterceptor}。</p>
 */
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    /** 时间格式化 */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminAuditService adminAuditService;

    /**
     * 顶部统计
     *
     * @return {pendingNotes, pendingFeedback, subscriptions, processed}
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(adminAuditService.stats());
    }

    /* ------------------------------ 用户笔记 ------------------------------ */

    /**
     * 笔记分页列表
     *
     * @param keyword 关键词（标题 / 正文，可选）
     * @param status  审核状态：0-待审核，1-已通过，2-已拒绝；不传为全部
     * @param page    页码，默认 1
     * @param size    每页大小，默认 10
     * @return 分页结果
     */
    @GetMapping("/note/list")
    public Result<PageResult<AdminNoteVO>> noteList(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(adminAuditService.pageNotes(keyword, status, page, size));
    }

    /**
     * 通过笔记
     *
     * @param id 笔记ID
     * @return 空结果
     */
    @PutMapping("/note/{id}/approve")
    public Result<Void> approveNote(@PathVariable Long id) {
        adminAuditService.approveNote(id);
        return Result.ok();
    }

    /**
     * 拒绝笔记
     *
     * @param id   笔记ID
     * @param body 请求体：{reason}
     * @return 空结果
     */
    @PutMapping("/note/{id}/reject")
    public Result<Void> rejectNote(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object reason = body.get("reason");
        adminAuditService.rejectNote(id, reason == null ? null : reason.toString());
        return Result.ok();
    }

    /**
     * 删除笔记（软删除）
     *
     * @param id 笔记ID
     * @return 空结果
     */
    @DeleteMapping("/note/{id}")
    public Result<Void> deleteNote(@PathVariable Long id) {
        adminAuditService.removeNote(id);
        return Result.ok();
    }

    /* ------------------------------ 用户反馈 ------------------------------ */

    /**
     * 反馈分页列表
     *
     * @param status  状态：0-待处理，1-已处理；不传为全部
     * @param keyword 内容关键词（可选）
     * @param page    页码，默认 1
     * @param size    每页大小，默认 10
     * @return 分页结果
     */
    @GetMapping("/feedback/list")
    public Result<PageResult<FeedbackVO>> feedbackList(@RequestParam(required = false) Integer status,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(adminAuditService.pageFeedback(status, keyword, page, size));
    }

    /**
     * 处理反馈（标记状态 / 回复 / 可选发送邮件）
     *
     * @param id   反馈ID
     * @param body 请求体：{status, reply, notifyEmail}
     * @return 空结果
     */
    @PutMapping("/feedback/{id}/handle")
    public Result<Void> handleFeedback(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object status = body.get("status");
        Object reply = body.get("reply");
        Object notify = body.get("notifyEmail");
        adminAuditService.handleFeedback(
                id,
                status == null ? 1 : Integer.valueOf(status.toString()),
                reply == null ? null : reply.toString(),
                notify != null && Boolean.parseBoolean(notify.toString()));
        return Result.ok();
    }

    /**
     * 回复反馈（发送邮件并标记已处理）
     *
     * @param id   反馈ID
     * @param body 请求体：{reply}
     * @return 空结果
     */
    @PostMapping("/feedback/{id}/reply")
    public Result<Void> replyFeedback(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object reply = body.get("reply");
        adminAuditService.replyFeedback(id, reply == null ? null : reply.toString());
        return Result.ok();
    }

    /**
     * 删除反馈（软删除）
     *
     * @param id 反馈ID
     * @return 空结果
     */
    @DeleteMapping("/feedback/{id}")
    public Result<Void> deleteFeedback(@PathVariable Long id) {
        adminAuditService.removeFeedback(id);
        return Result.ok();
    }

    /* ------------------------------ 邮箱订阅 ------------------------------ */

    /**
     * 订阅分页列表
     *
     * @param keyword 邮箱关键词（可选）
     * @param page    页码，默认 1
     * @param size    每页大小，默认 10
     * @return 分页结果
     */
    @GetMapping("/subscription/list")
    public Result<PageResult<EmailSubscription>> subscriptionList(@RequestParam(required = false) String keyword,
                                                                  @RequestParam(defaultValue = "1") Integer page,
                                                                  @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(adminAuditService.pageSubscriptions(keyword, page, size));
    }

    /**
     * 删除邮箱订阅（软删除）
     *
     * @param id 订阅ID
     * @return 空结果
     */
    @DeleteMapping("/subscription/{id}")
    public Result<Void> deleteSubscription(@PathVariable Long id) {
        adminAuditService.removeSubscription(id);
        return Result.ok();
    }

    /**
     * 导出邮箱订阅为 Excel
     *
     * @param response 响应
     * @throws IOException 写出异常
     */
    @GetMapping("/subscription/export")
    public void exportSubscriptions(HttpServletResponse response) throws IOException {
        List<EmailSubscription> list = adminAuditService.listAllSubscriptions();
        String[] headers = {"ID", "邮箱", "订阅来源", "订阅时间"};
        List<String[]> rows = new ArrayList<>(list.size());
        for (EmailSubscription item : list) {
            rows.add(new String[]{
                    String.valueOf(item.getId()),
                    item.getEmail(),
                    item.getSource(),
                    item.getCreateTime() == null ? "" : DATE_TIME.format(item.getCreateTime())
            });
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=subscriptions.xlsx");
        SimpleExcelWriter.write(response.getOutputStream(), "邮箱订阅", headers, rows);
    }
}
