package com.shuyun.web.controller;

import com.shuyun.common.PageResult;
import com.shuyun.common.Result;
import com.shuyun.pojo.entity.UserViewHistory;
import com.shuyun.service.HistoryService;
import com.shuyun.web.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 浏览足迹接口（需登录）
 */
@RestController
@RequestMapping("/api/user/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    /**
     * 分页查询浏览足迹（按最近浏览倒序）
     *
     * @param page 页码，默认 1
     * @param size 每页大小，默认 12
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<UserViewHistory>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        return Result.ok(historyService.pageHistory(UserContext.getUserId(), page, size));
    }

    /**
     * 记录一次浏览（同一对象重复浏览只刷新时间）
     *
     * @param body {targetType, targetId}
     * @return 空结果
     */
    @PostMapping
    public Result<Void> record(@RequestBody Map<String, Object> body) {
        Object type = body.get("targetType");
        Object id = body.get("targetId");
        historyService.record(
                UserContext.getUserId(),
                type == null ? null : String.valueOf(type),
                id == null ? null : Long.valueOf(String.valueOf(id)));
        return Result.ok();
    }

    /**
     * 删除一条足迹
     *
     * @param id 足迹ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        historyService.removeOne(UserContext.getUserId(), id);
        return Result.ok();
    }

    /**
     * 清空当前用户全部足迹
     *
     * @return 空结果
     */
    @DeleteMapping
    public Result<Void> clear() {
        historyService.clear(UserContext.getUserId());
        return Result.ok();
    }
}
