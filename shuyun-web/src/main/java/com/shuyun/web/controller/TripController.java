package com.shuyun.web.controller;

import com.shuyun.common.Result;
import com.shuyun.pojo.dto.TripSaveDTO;
import com.shuyun.pojo.vo.UserTripVO;
import com.shuyun.service.TripService;
import com.shuyun.web.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 我的行程接口
 */
@RestController
@RequestMapping("/api/user/trip")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    /**
     * 我的行程列表（含每日明细）
     *
     * @return 行程列表
     */
    @GetMapping
    public Result<List<UserTripVO>> list() {
        return Result.ok(tripService.listMyTrips(UserContext.getUserId()));
    }

    /**
     * 新建行程
     *
     * @param dto 行程数据
     * @return 新行程ID
     */
    @PostMapping
    public Result<Long> create(@RequestBody TripSaveDTO dto) {
        return Result.ok(tripService.saveTrip(UserContext.getUserId(), null, dto));
    }

    /**
     * 编辑行程
     *
     * @param id  行程ID
     * @param dto 行程数据
     * @return 统一结果
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TripSaveDTO dto) {
        tripService.saveTrip(UserContext.getUserId(), id, dto);
        return Result.ok();
    }

    /**
     * 删除行程
     *
     * @param id 行程ID
     * @return 统一结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tripService.deleteTrip(UserContext.getUserId(), id);
        return Result.ok();
    }
}
