package com.shuyun.web.controller;

import com.shuyun.common.PageResult;
import com.shuyun.common.Result;
import com.shuyun.pojo.vo.AdminTripDetailVO;
import com.shuyun.pojo.vo.AdminTripVO;
import com.shuyun.service.AdminTripService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 行程管理接口（后台，仅管理员）
 *
 * <p>权限校验见 {@code AdminInterceptor}。</p>
 */
@RestController
@RequestMapping("/api/admin/trip")
@RequiredArgsConstructor
public class AdminTripController {

    private final AdminTripService adminTripService;

    /**
     * 顶部统计
     *
     * @return {total, today, avgDays, avgScenic}
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(adminTripService.stats());
    }

    /**
     * 行程分页列表
     *
     * @param keyword   关键词（标题 / 用户名，可选）
     * @param days      天数区间：1-2 / 3-4 / 5-7 / 7+（7 天以上），可选
     * @param companion 同行人（可选）
     * @param page      页码，默认 1
     * @param size      每页大小，默认 10
     * @return 分页结果
     */
    @GetMapping("/list")
    public Result<PageResult<AdminTripVO>> list(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String days,
                                                @RequestParam(required = false) String companion,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "10") Integer size) {
        // 天数区间下拉：1-2 / 3-4 / 5-7 / 7+（7 天以上）
        Integer minDays = null;
        Integer maxDays = null;
        if (days != null) {
            switch (days) {
                case "1-2" -> {
                    minDays = 1;
                    maxDays = 2;
                }
                case "3-4" -> {
                    minDays = 3;
                    maxDays = 4;
                }
                case "5-7" -> {
                    minDays = 5;
                    maxDays = 7;
                }
                case "7+" -> minDays = 8;
                default -> {
                    // 非法值：忽略，不参与筛选
                }
            }
        }
        return Result.ok(adminTripService.pageTrips(keyword, minDays, maxDays, companion, page, size));
    }

    /**
     * 行程详情（含每日明细）
     *
     * @param id 行程ID
     * @return 行程详情
     */
    @GetMapping("/{id}")
    public Result<AdminTripDetailVO> detail(@PathVariable Long id) {
        return Result.ok(adminTripService.getDetail(id));
    }

    /**
     * 删除行程（软删除）
     *
     * @param id 行程ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminTripService.removeTrip(id);
        return Result.ok();
    }
}
