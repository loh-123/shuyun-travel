package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.pojo.entity.UserCollection;
import com.shuyun.pojo.vo.ScenicSpotVO;

import java.util.List;

/**
 * 用户收藏业务接口
 */
public interface CollectionService extends IService<UserCollection> {

    /**
     * 收藏（已收藏则报错）
     *
     * @param userId     用户ID
     * @param targetType 收藏对象类型，如 SCENIC
     * @param targetId   收藏对象ID
     */
    void collect(Long userId, String targetType, Long targetId);

    /**
     * 取消收藏
     *
     * @param userId     用户ID
     * @param targetType 收藏对象类型，如 SCENIC
     * @param targetId   收藏对象ID
     */
    void remove(Long userId, String targetType, Long targetId);

    /**
     * 是否已收藏
     *
     * @param userId     用户ID
     * @param targetType 收藏对象类型，如 SCENIC
     * @param targetId   收藏对象ID
     * @return true-已收藏
     */
    boolean isCollected(Long userId, String targetType, Long targetId);

    /**
     * 查询用户收藏的景点列表（按收藏时间倒序）
     *
     * @param userId 用户ID
     * @return 景点列表
     */
    List<ScenicSpotVO> listScenicCollections(Long userId);
}
