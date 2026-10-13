package com.shuyun.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuyun.common.PageResult;
import com.shuyun.common.Result;
import com.shuyun.pojo.vo.AdminChatSessionVO;
import com.shuyun.service.AdminAiChatService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 对话管理接口（后台，仅管理员）
 *
 * <p>权限校验见 {@code AdminInterceptor}。</p>
 */
@RestController
@RequestMapping("/api/admin/ai/chat")
@RequiredArgsConstructor
public class AdminAiChatController {

    /** 导出文件名时间格式 */
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AdminAiChatService adminAiChatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 会话分页列表
     *
     * @param keyword   关键词（用户名 / 提问内容，可选）
     * @param intent    意图分类（可选）
     * @param startDate 起始日期（yyyy-MM-dd，可选）
     * @param endDate   结束日期（yyyy-MM-dd，可选）
     * @param page      页码，默认 1
     * @param size      每页大小，默认 10
     * @return 分页结果
     */
    @GetMapping("/list")
    public Result<PageResult<AdminChatSessionVO>> list(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String intent,
                                                       @RequestParam(required = false) String startDate,
                                                       @RequestParam(required = false) String endDate,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(adminAiChatService.pageSessions(keyword, intent, startDate, endDate, page, size));
    }

    /**
     * 顶部统计
     *
     * @return {total, today, avgRounds, satisfaction}
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(adminAiChatService.stats());
    }

    /**
     * 导出训练语料（JSON）
     *
     * @param ids      会话ID列表（逗号分隔）
     * @param response 响应
     * @throws IOException 写出异常
     */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String ids, HttpServletResponse response) throws IOException {
        List<String> sessionIds = StringUtils.hasText(ids)
                ? Arrays.stream(ids.split(",")).map(String::trim).filter(StringUtils::hasText).collect(Collectors.toList())
                : List.of();
        List<Map<String, Object>> corpus = adminAiChatService.exportSessions(sessionIds);

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=training_corpus_" + FILE_TIME.format(LocalDateTime.now()) + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(response.getOutputStream(), corpus);
    }

    /**
     * 会话详情（完整对话）
     *
     * @param sessionId 会话ID
     * @return {session, messages}
     */
    @GetMapping("/{sessionId}")
    public Result<Map<String, Object>> detail(@PathVariable String sessionId) {
        return Result.ok(adminAiChatService.getSessionDetail(sessionId));
    }

    /**
     * 标记会话（1-优质对话，2-训练语料，0-取消）
     *
     * @param sessionId 会话ID
     * @param body      请求体：{markType}
     * @return 空结果
     */
    @PostMapping("/{sessionId}/mark")
    public Result<Void> mark(@PathVariable String sessionId, @RequestBody Map<String, Object> body) {
        Object markType = body.get("markType");
        adminAiChatService.mark(sessionId, markType == null ? 0 : Integer.valueOf(markType.toString()));
        return Result.ok();
    }

    /**
     * 删除会话（软删除）
     *
     * @param sessionId 会话ID
     * @return 空结果
     */
    @DeleteMapping("/{sessionId}")
    public Result<Void> delete(@PathVariable String sessionId) {
        adminAiChatService.removeSession(sessionId);
        return Result.ok();
    }
}
