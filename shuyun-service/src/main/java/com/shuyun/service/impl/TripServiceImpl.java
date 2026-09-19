package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.BusinessException;
import com.shuyun.mapper.UserTripDayMapper;
import com.shuyun.mapper.UserTripMapper;
import com.shuyun.pojo.dto.TripSaveDTO;
import com.shuyun.pojo.entity.UserTrip;
import com.shuyun.pojo.entity.UserTripDay;
import com.shuyun.pojo.vo.UserTripVO;
import com.shuyun.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户行程业务实现
 */
@Service
@RequiredArgsConstructor
public class TripServiceImpl extends ServiceImpl<UserTripMapper, UserTrip> implements TripService {

    /** 默认状态：1-正常 */
    private static final Integer DEFAULT_STATUS = 1;

    private final UserTripDayMapper userTripDayMapper;

    @Override
    public List<UserTripVO> listMyTrips(Long userId) {
        List<UserTrip> trips = list(Wrappers.<UserTrip>lambdaQuery()
                .eq(UserTrip::getUserId, userId)
                .orderByDesc(UserTrip::getId));
        if (trips.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> tripIds = trips.stream().map(UserTrip::getId).collect(Collectors.toList());
        Map<Long, List<UserTripDay>> dayMap = userTripDayMapper
                .selectList(Wrappers.<UserTripDay>lambdaQuery()
                        .in(UserTripDay::getTripId, tripIds)
                        .orderByAsc(UserTripDay::getDayIndex)
                        .orderByAsc(UserTripDay::getId))
                .stream()
                .collect(Collectors.groupingBy(UserTripDay::getTripId));

        return trips.stream().map(trip -> {
            UserTripVO vo = new UserTripVO();
            BeanUtils.copyProperties(trip, vo);
            vo.setDayList(dayMap.getOrDefault(trip.getId(), Collections.emptyList()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveTrip(Long userId, Long tripId, TripSaveDTO dto) {
        UserTrip trip = new UserTrip();
        BeanUtils.copyProperties(dto, trip, "dayList");
        trip.setUserId(userId);
        if (trip.getStatus() == null) {
            trip.setStatus(DEFAULT_STATUS);
        }

        if (tripId == null) {
            save(trip);
        } else {
            trip.setId(tripId);
            updateById(trip);
            // 覆盖式更新：先清空原有每日明细
            userTripDayMapper.delete(Wrappers.<UserTripDay>lambdaQuery()
                    .eq(UserTripDay::getTripId, tripId));
        }

        Long id = trip.getId();
        saveDays(id, dto.getDayList());
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTrip(Long userId, Long tripId) {
        UserTrip trip = getById(tripId);
        if (trip == null || !trip.getUserId().equals(userId)) {
            throw new BusinessException("行程不存在");
        }
        removeById(tripId);
        userTripDayMapper.delete(Wrappers.<UserTripDay>lambdaQuery()
                .eq(UserTripDay::getTripId, tripId));
    }

    /**
     * 保存每日明细
     */
    private void saveDays(Long tripId, List<UserTripDay> dayList) {
        if (dayList == null || dayList.isEmpty()) {
            return;
        }
        int index = 0;
        for (UserTripDay day : dayList) {
            day.setId(null);
            day.setTripId(tripId);
            if (day.getDayIndex() == null) {
                day.setDayIndex(++index);
            }
            userTripDayMapper.insert(day);
        }
    }
}
