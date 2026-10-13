package com.shuyun.service;

import com.shuyun.common.PageResult;
import com.shuyun.pojo.vo.AdminTripDetailVO;
import com.shuyun.pojo.vo.AdminTripVO;

import java.util.Map;

/**
 * 后台行程管理业务接口
 */
public interface AdminTripService {

    /**
     * 行程分页查询（含所属用户名 / 景点数）
     *
     * @param keyword   关键词（标题 / 用户名，可选）
     * @param minDays   最小天数（可选）
     * @param maxDays   最大天数（可选）
     * @param companion 同行人（可选）
     * @param page      页码
     * @param size      每页大小
     * @return 分页结果
     */
    PageResult<AdminTripVO> pageTrips(String keyword, Integer minDays, Integer maxDays, String companion,
                                      int page, int size);

    /**
     * 行程详情（含每日明细）
     *
     * @param id 行程ID
     * @return 行程详情
     */
    AdminTripDetailVO getDetail(Long id);

    /**
     * 删除行程（软删除，同时软删除每日明细）
     *
     * @param id 行程ID
     */
    void removeTrip(Long id);

    /**
     * 顶部统计：总行程数 / 今日新增 / 平均天数 / 平均景点数
     *
     * @return {total, today, avgDays, avgScenic}
     */
    Map<String, Object> stats();
}
