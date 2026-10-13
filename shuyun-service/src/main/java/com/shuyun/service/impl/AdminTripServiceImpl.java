package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shuyun.common.BusinessException;
import com.shuyun.common.PageResult;
import com.shuyun.mapper.AdminStatMapper;
import com.shuyun.mapper.AdminTripMapper;
import com.shuyun.mapper.UserTripDayMapper;
import com.shuyun.mapper.UserTripMapper;
import com.shuyun.pojo.entity.User;
import com.shuyun.pojo.entity.UserTrip;
import com.shuyun.pojo.entity.UserTripDay;
import com.shuyun.pojo.vo.AdminTripDetailVO;
import com.shuyun.pojo.vo.AdminTripVO;
import com.shuyun.service.AdminTripService;
import com.shuyun.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台行程管理业务实现
 */
@Service
@RequiredArgsConstructor
public class AdminTripServiceImpl implements AdminTripService {

    private final UserTripMapper userTripMapper;

    private final UserTripDayMapper userTripDayMapper;

    private final AdminTripMapper adminTripMapper;

    private final AdminStatMapper adminStatMapper;

    private final UserService userService;

    @Override
    public PageResult<AdminTripVO> pageTrips(String keyword, Integer minDays, Integer maxDays, String companion,
                                             int page, int size) {
        LambdaQueryWrapper<UserTrip> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(UserTrip::getDeleted, 0)
                .eq(StringUtils.hasText(companion), UserTrip::getCompanion, companion)
                .ge(minDays != null, UserTrip::getDays, minDays)
                .le(maxDays != null, UserTrip::getDays, maxDays);
        if (StringUtils.hasText(keyword)) {
            // 关键词同时匹配标题与用户名：先按用户名找出用户ID
            List<Long> matchedUserIds = userService.list(Wrappers.<User>lambdaQuery()
                            .like(User::getUsername, keyword))
                    .stream().map(User::getId).collect(Collectors.toList());
            wrapper.and(w -> {
                w.like(UserTrip::getTitle, keyword);
                if (!matchedUserIds.isEmpty()) {
                    w.or().in(UserTrip::getUserId, matchedUserIds);
                }
            });
        }
        wrapper.orderByDesc(UserTrip::getCreateTime);

        Page<UserTrip> result = userTripMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserTrip> trips = result.getRecords();

        Map<Long, User> userMap = userMap(trips);
        Map<Long, int[]> aggMap = dayAggregate(trips.stream().map(UserTrip::getId).collect(Collectors.toList()));

        List<AdminTripVO> records = new ArrayList<>(trips.size());
        for (UserTrip trip : trips) {
            AdminTripVO vo = new AdminTripVO();
            BeanUtils.copyProperties(trip, vo);
            User owner = userMap.get(trip.getUserId());
            vo.setUsername(owner == null ? "未知用户" : owner.getUsername());
            vo.setAvatar(owner == null ? null : owner.getAvatar());
            int[] agg = aggMap.getOrDefault(trip.getId(), new int[]{0, 0});
            vo.setDayCount(agg[0]);
            vo.setScenicCount(agg[1]);
            records.add(vo);
        }
        return PageResult.of(result.getTotal(), records, page, size);
    }

    @Override
    public AdminTripDetailVO getDetail(Long id) {
        UserTrip trip = userTripMapper.selectById(id);
        if (trip == null || Integer.valueOf(1).equals(trip.getDeleted())) {
            throw new BusinessException("行程不存在");
        }

        AdminTripDetailVO vo = new AdminTripDetailVO();
        BeanUtils.copyProperties(trip, vo);
        User owner = userMap(Collections.singletonList(trip)).get(trip.getUserId());
        vo.setUsername(owner == null ? "未知用户" : owner.getUsername());
        vo.setAvatar(owner == null ? null : owner.getAvatar());

        List<UserTripDay> days = userTripDayMapper.selectList(Wrappers.<UserTripDay>lambdaQuery()
                .eq(UserTripDay::getTripId, id)
                .eq(UserTripDay::getDeleted, 0)
                .orderByAsc(UserTripDay::getDayIndex)
                .orderByAsc(UserTripDay::getId));
        vo.setDayList(days);
        vo.setDayCount(days.size());
        vo.setScenicCount((int) days.stream()
                .map(UserTripDay::getScenicId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTrip(Long id) {
        userTripMapper.update(null, Wrappers.<UserTrip>lambdaUpdate()
                .set(UserTrip::getDeleted, 1)
                .eq(UserTrip::getId, id));
        userTripDayMapper.update(null, Wrappers.<UserTripDay>lambdaUpdate()
                .set(UserTripDay::getDeleted, 1)
                .eq(UserTripDay::getTripId, id));
    }

    @Override
    public Map<String, Object> stats() {
        long total = adminTripMapper.countTrips();
        long today = adminTripMapper.countTripsToday();
        long yesterday = adminTripMapper.countTripsYesterday();
        double avgDays = round1(adminTripMapper.avgDays());
        double avgScenic = round1(adminTripMapper.avgScenicPerTrip());

        // 环比：今日 vs 昨日（平均项按当日新增行程计算）
        String todayStr = LocalDate.now().toString();
        String yesterdayStr = LocalDate.now().minusDays(1).toString();
        double avgDaysToday = round1(adminTripMapper.avgDaysOn(todayStr));
        double avgDaysYesterday = round1(adminTripMapper.avgDaysOn(yesterdayStr));
        double avgScenicToday = round1(adminTripMapper.avgScenicOn(todayStr));
        double avgScenicYesterday = round1(adminTripMapper.avgScenicOn(yesterdayStr));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("today", today);
        data.put("avgDays", avgDays);
        data.put("avgScenic", avgScenic);
        data.put("totalTrend", growth(today, yesterday));
        data.put("todayTrend", growth(today, yesterday));
        data.put("avgDaysTrend", growthD(avgDaysToday, avgDaysYesterday));
        data.put("avgScenicTrend", growthD(avgScenicToday, avgScenicYesterday));
        data.put("spark", recent7Days(adminStatMapper.tripDailyTrend()));
        return data;
    }

    /**
     * 把 [{date,count}] 补零为最近 7 天（旧→新）的序列
     */
    private List<Integer> recent7Days(List<Map<String, Object>> rows) {
        Map<String, Integer> byDate = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object date = row.get("date");
                if (date == null) {
                    date = row.get("DATE");
                }
                if (date != null) {
                    Object count = row.get("count");
                    byDate.put(String.valueOf(date), count == null ? 0 : ((Number) count).intValue());
                }
            }
        }
        List<Integer> series = new ArrayList<>(7);
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            series.add(byDate.getOrDefault(today.minusDays(i).toString(), 0));
        }
        return series;
    }

    /**
     * 批量取「用户ID -> 用户」
     */
    private Map<Long, User> userMap(List<UserTrip> trips) {
        Set<Long> userIds = trips.stream()
                .map(UserTrip::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> map = new HashMap<>();
        if (!userIds.isEmpty()) {
            userService.listByIds(userIds).forEach(u -> map.put(u.getId(), u));
        }
        return map;
    }

    /**
     * 统计每个行程的 [每日明细条数, 去重景点数]
     */
    private Map<Long, int[]> dayAggregate(List<Long> tripIds) {
        Map<Long, int[]> map = new HashMap<>();
        if (tripIds.isEmpty()) {
            return map;
        }
        List<UserTripDay> days = userTripDayMapper.selectList(Wrappers.<UserTripDay>lambdaQuery()
                .in(UserTripDay::getTripId, tripIds)
                .eq(UserTripDay::getDeleted, 0));
        Map<Long, Set<Long>> scenicSets = new HashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        for (UserTripDay day : days) {
            counts.merge(day.getTripId(), 1, Integer::sum);
            if (day.getScenicId() != null) {
                scenicSets.computeIfAbsent(day.getTripId(), k -> new HashSet<>()).add(day.getScenicId());
            }
        }
        counts.forEach((tripId, count) -> map.put(tripId,
                new int[]{count, scenicSets.getOrDefault(tripId, Collections.emptySet()).size()}));
        return map;
    }

    /** 保留一位小数 */
    private double round1(Double value) {
        if (value == null) {
            return 0.0;
        }
        return Math.round(value * 10) / 10.0;
    }

    /** 环比增长率（整数） */
    private double growth(long today, long yesterday) {
        if (yesterday == 0) {
            return today == 0 ? 0.0 : 100.0;
        }
        return Math.round((today - yesterday) * 1000.0 / yesterday) / 10.0;
    }

    /** 环比增长率（小数） */
    private double growthD(double today, double yesterday) {
        if (yesterday == 0) {
            return today == 0 ? 0.0 : 100.0;
        }
        return Math.round((today - yesterday) * 1000.0 / yesterday) / 10.0;
    }
}
