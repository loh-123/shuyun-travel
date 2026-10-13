package com.shuyun.web.controller;

import com.shuyun.common.Result;
import com.shuyun.pojo.vo.ActiveUserVO;
import com.shuyun.pojo.vo.BehaviorStatsVO;
import com.shuyun.pojo.vo.BehaviorTrendVO;
import com.shuyun.pojo.vo.DistributionItemVO;
import com.shuyun.service.AdminBehaviorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户行为统计接口（后台，仅管理员）
 *
 * <p>权限校验见 {@code AdminInterceptor}。</p>
 */
@RestController
@RequestMapping("/api/admin/behavior")
@RequiredArgsConstructor
public class AdminBehaviorController {

    private final AdminBehaviorService adminBehaviorService;

    /**
     * 顶部统计卡片
     *
     * @return 统计卡片数据
     */
    @GetMapping("/stats")
    public Result<BehaviorStatsVO> stats() {
        return Result.ok(adminBehaviorService.stats());
    }

    /**
     * 近 N 天行为趋势
     *
     * @param days 天数，默认 30
     * @return 趋势数据
     */
    @GetMapping("/trend")
    public Result<BehaviorTrendVO> trend(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(adminBehaviorService.trend(days));
    }

    /**
     * 功能使用占比
     *
     * @return [{name, value}]
     */
    @GetMapping("/distribution")
    public Result<List<DistributionItemVO>> distribution() {
        return Result.ok(adminBehaviorService.distribution());
    }

    /**
     * 热门浏览景点 TOP N
     *
     * @param limit 数量，默认 10
     * @return [{name, value}]
     */
    @GetMapping("/hot-scenic")
    public Result<List<DistributionItemVO>> hotScenic(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(adminBehaviorService.hotScenic(limit));
    }

    /**
     * 活跃用户 TOP N
     *
     * @param limit 数量，默认 10
     * @return 活跃用户列表
     */
    @GetMapping("/active-user")
    public Result<List<ActiveUserVO>> activeUser(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(adminBehaviorService.activeUsers(limit));
    }

    /**
     * 最近用户行为动态
     *
     * @param limit 数量，默认 12
     * @return [{username, action, target, time}]
     */
    @GetMapping("/recent")
    public Result<List<Map<String, Object>>> recent(@RequestParam(defaultValue = "12") int limit) {
        return Result.ok(adminBehaviorService.recent(limit));
    }
}
