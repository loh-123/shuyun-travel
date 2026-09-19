package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.PageResult;
import com.shuyun.mapper.CityMapper;
import com.shuyun.mapper.CultureMapper;
import com.shuyun.mapper.ScenicSpotMapper;
import com.shuyun.mapper.UserViewHistoryMapper;
import com.shuyun.pojo.entity.City;
import com.shuyun.pojo.entity.Culture;
import com.shuyun.pojo.entity.ScenicSpot;
import com.shuyun.pojo.entity.UserViewHistory;
import com.shuyun.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 浏览足迹业务实现
 */
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl extends ServiceImpl<UserViewHistoryMapper, UserViewHistory>
        implements HistoryService {

    /** 对象类型：景点 */
    private static final String TYPE_SCENIC = "SCENIC";

    /** 对象类型：文化 */
    private static final String TYPE_CULTURE = "CULTURE";

    private final ScenicSpotMapper scenicSpotMapper;

    private final CultureMapper cultureMapper;

    private final CityMapper cityMapper;

    @Override
    public void record(Long userId, String targetType, Long targetId) {
        if (userId == null || targetId == null) {
            return;
        }
        String type = StringUtils.hasText(targetType) ? targetType.trim().toUpperCase() : TYPE_SCENIC;

        UserViewHistory row = getOne(Wrappers.<UserViewHistory>lambdaQuery()
                .eq(UserViewHistory::getUserId, userId)
                .eq(UserViewHistory::getTargetType, type)
                .eq(UserViewHistory::getTargetId, targetId)
                .last("limit 1"));

        boolean isNew = row == null;
        if (isNew) {
            row = new UserViewHistory();
        }
        row.setUserId(userId);
        row.setTargetType(type);
        row.setTargetId(targetId);
        row.setViewTime(LocalDateTime.now());

        // 冗余快照：列表页直接展示，不再联表；对象不存在则不记录
        if (TYPE_SCENIC.equals(type)) {
            ScenicSpot spot = scenicSpotMapper.selectById(targetId);
            if (spot == null) {
                return;
            }
            row.setTargetName(spot.getName());
            row.setCoverImage(spot.getCoverImage());
            row.setSubTitle(cityNameOf(spot.getCityId(), spot.getType()));
        } else if (TYPE_CULTURE.equals(type)) {
            Culture culture = cultureMapper.selectById(targetId);
            if (culture == null) {
                return;
            }
            row.setTargetName(culture.getName());
            row.setCoverImage(culture.getCoverImage());
            row.setSubTitle(culture.getCategory());
        } else {
            return;
        }

        if (isNew) {
            save(row);
        } else {
            updateById(row);
        }
    }

    @Override
    public PageResult<UserViewHistory> pageHistory(Long userId, int page, int size) {
        Page<UserViewHistory> result = page(new Page<>(page, size),
                Wrappers.<UserViewHistory>lambdaQuery()
                        .eq(UserViewHistory::getUserId, userId)
                        // 同一秒内的多条按 id 兜底，保证顺序稳定
                        .orderByDesc(UserViewHistory::getViewTime)
                        .orderByDesc(UserViewHistory::getId));
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Override
    public void removeOne(Long userId, Long id) {
        remove(Wrappers.<UserViewHistory>lambdaQuery()
                .eq(UserViewHistory::getUserId, userId)
                .eq(UserViewHistory::getId, id));
    }

    @Override
    public void clear(Long userId) {
        remove(Wrappers.<UserViewHistory>lambdaQuery().eq(UserViewHistory::getUserId, userId));
    }

    /**
     * 取市州名称；取不到时退回传入的兜底文案
     */
    private String cityNameOf(Long cityId, String fallback) {
        if (cityId == null) {
            return fallback;
        }
        City city = cityMapper.selectById(cityId);
        return city == null ? fallback : city.getName();
    }
}
