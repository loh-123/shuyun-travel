package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.pojo.dto.TripSaveDTO;
import com.shuyun.pojo.entity.UserTrip;
import com.shuyun.pojo.vo.UserTripVO;

import java.util.List;

/**
 * 用户行程业务接口
 */
public interface TripService extends IService<UserTrip> {

    /**
     * 查询当前用户全部行程（含每日明细）
     *
     * @param userId 用户ID
     * @return 行程列表
     */
    List<UserTripVO> listMyTrips(Long userId);

    /**
     * 保存行程：tripId 为空则新增，否则覆盖更新（每日明细整体替换）
     *
     * @param userId 用户ID
     * @param tripId 行程ID，null 表示新增
     * @param dto    行程数据
     * @return 行程ID
     */
    Long saveTrip(Long userId, Long tripId, TripSaveDTO dto);

    /**
     * 删除行程及其每日明细
     *
     * @param userId 用户ID
     * @param tripId 行程ID
     */
    void deleteTrip(Long userId, Long tripId);
}
