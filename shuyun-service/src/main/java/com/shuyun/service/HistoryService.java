package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.common.PageResult;
import com.shuyun.pojo.entity.UserViewHistory;

/**
 * 浏览足迹业务接口
 */
public interface HistoryService extends IService<UserViewHistory> {

    /**
     * 记录一次浏览
     *
     * <p>同一用户对同一对象只保留一条，重复浏览时刷新浏览时间；
     * 同时冗余一份名称 / 封面 / 副标题，列表页无需联表。</p>
     *
     * @param userId     用户ID
     * @param targetType 对象类型：SCENIC / CULTURE
     * @param targetId   对象ID
     */
    void record(Long userId, String targetType, Long targetId);

    /**
     * 分页查询足迹（按最近浏览倒序）
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 分页结果
     */
    PageResult<UserViewHistory> pageHistory(Long userId, int page, int size);

    /**
     * 删除一条足迹
     *
     * @param userId 用户ID
     * @param id     足迹ID
     */
    void removeOne(Long userId, Long id);

    /**
     * 清空该用户的全部足迹
     *
     * @param userId 用户ID
     */
    void clear(Long userId);

    /**
     * 统计「去过的城市」：足迹里景点所属市州去重后的数量
     *
     * @param userId 用户ID
     * @return 城市数
     */
    int countCities(Long userId);
}
