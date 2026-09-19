package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.BusinessException;
import com.shuyun.mapper.CityMapper;
import com.shuyun.mapper.ScenicSpotMapper;
import com.shuyun.mapper.UserCollectionMapper;
import com.shuyun.pojo.entity.City;
import com.shuyun.pojo.entity.ScenicSpot;
import com.shuyun.pojo.entity.UserCollection;
import com.shuyun.pojo.vo.ScenicSpotVO;
import com.shuyun.service.CollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户收藏业务实现
 */
@Service
@RequiredArgsConstructor
public class CollectionServiceImpl extends ServiceImpl<UserCollectionMapper, UserCollection>
        implements CollectionService {

    /** 收藏对象类型：景点 */
    private static final String TYPE_SCENIC = "SCENIC";

    private final ScenicSpotMapper scenicSpotMapper;

    private final CityMapper cityMapper;

    @Override
    public void collect(Long userId, String targetType, Long targetId) {
        String type = (targetType == null || targetType.isBlank()) ? TYPE_SCENIC : targetType;

        Long exists = count(Wrappers.<UserCollection>lambdaQuery()
                .eq(UserCollection::getUserId, userId)
                .eq(UserCollection::getTargetType, type)
                .eq(UserCollection::getTargetId, targetId));
        if (exists != null && exists > 0) {
            throw new BusinessException("已经收藏过了");
        }

        UserCollection collection = new UserCollection();
        collection.setUserId(userId);
        collection.setTargetType(type);
        collection.setTargetId(targetId);
        save(collection);
    }

    @Override
    public void remove(Long userId, String targetType, Long targetId) {
        String type = (targetType == null || targetType.isBlank()) ? TYPE_SCENIC : targetType;
        remove(Wrappers.<UserCollection>lambdaQuery()
                .eq(UserCollection::getUserId, userId)
                .eq(UserCollection::getTargetType, type)
                .eq(UserCollection::getTargetId, targetId));
    }

    @Override
    public boolean isCollected(Long userId, String targetType, Long targetId) {
        String type = (targetType == null || targetType.isBlank()) ? TYPE_SCENIC : targetType;
        Long exists = count(Wrappers.<UserCollection>lambdaQuery()
                .eq(UserCollection::getUserId, userId)
                .eq(UserCollection::getTargetType, type)
                .eq(UserCollection::getTargetId, targetId));
        return exists != null && exists > 0;
    }

    @Override
    public List<ScenicSpotVO> listScenicCollections(Long userId) {
        List<UserCollection> rows = list(Wrappers.<UserCollection>lambdaQuery()
                .eq(UserCollection::getUserId, userId)
                .eq(UserCollection::getTargetType, TYPE_SCENIC)
                .orderByDesc(UserCollection::getId));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> scenicIds = rows.stream().map(UserCollection::getTargetId).collect(Collectors.toList());
        Map<Long, ScenicSpot> spotMap = scenicSpotMapper
                .selectList(Wrappers.<ScenicSpot>lambdaQuery().in(ScenicSpot::getId, scenicIds))
                .stream()
                .collect(Collectors.toMap(ScenicSpot::getId, s -> s, (a, b) -> a));

        Map<Long, String> cityNames = cityMapper.selectList(null).stream()
                .collect(Collectors.toMap(City::getId, City::getName, (a, b) -> a));

        List<ScenicSpotVO> result = new ArrayList<>();
        for (Long id : scenicIds) {
            ScenicSpot spot = spotMap.get(id);
            if (spot == null) {
                continue;
            }
            ScenicSpotVO vo = new ScenicSpotVO();
            BeanUtils.copyProperties(spot, vo);
            vo.setCityName(cityNames.get(spot.getCityId()));
            result.add(vo);
        }
        return result;
    }
}
