package com.shuyun.web.controller;

import com.shuyun.common.Result;
import com.shuyun.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台数据看板接口
 *
 * <p>仅管理员可访问，权限校验见 {@code AdminInterceptor}。</p>
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminController {

    private final AdminDashboardService adminDashboardService;

    /**
     * 顶部统计卡片
     *
     * @return {users, scenics, visits, chats}
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(adminDashboardService.stats());
    }

    /**
     * 近 N 天访问趋势
     *
     * @param days 天数，默认 30
     * @return [{date, fullDate, count}]
     */
    @GetMapping("/visit-trend")
    public Result<List<Map<String, Object>>> visitTrend(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(adminDashboardService.visitTrend(days));
    }

    /**
     * 用户兴趣分布
     *
     * @return [{name, value}]
     */
    @GetMapping("/interest-distribution")
    public Result<List<Map<String, Object>>> interestDistribution() {
        return Result.ok(adminDashboardService.interestDistribution());
    }

    /**
     * 热门景点 TOP N
     *
     * @param limit 数量，默认 10
     * @return [{name, value}]
     */
    @GetMapping("/hot-scenic")
    public Result<List<Map<String, Object>>> hotScenic(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(adminDashboardService.hotScenic(limit));
    }

    /**
     * 最近用户活动
     *
     * @param limit 数量，默认 10
     * @return [{username, action, target, time}]
     */
    @GetMapping("/recent-activities")
    public Result<List<Map<String, Object>>> recentActivities(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(adminDashboardService.recentActivities(limit));
    }
}
