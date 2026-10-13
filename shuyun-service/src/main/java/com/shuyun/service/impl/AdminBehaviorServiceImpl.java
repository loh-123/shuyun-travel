package com.shuyun.service.impl;

import com.shuyun.mapper.AdminBehaviorMapper;
import com.shuyun.pojo.vo.ActiveUserVO;
import com.shuyun.pojo.vo.BehaviorStatsVO;
import com.shuyun.pojo.vo.BehaviorTrendVO;
import com.shuyun.pojo.vo.DistributionItemVO;
import com.shuyun.service.AdminBehaviorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台用户行为统计业务实现
 */
@Service
@RequiredArgsConstructor
public class AdminBehaviorServiceImpl implements AdminBehaviorService {

    /** 环比基数 */
    private static final double GROWTH_BASE = 100.0;

    /** 趋势日期标签格式 */
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    /** 时间轴时间格式 */
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminBehaviorMapper adminBehaviorMapper;

    @Override
    public BehaviorStatsVO stats() {
        BehaviorStatsVO vo = new BehaviorStatsVO();
        vo.setTotalViews(adminBehaviorMapper.countViews());
        vo.setTotalCollections(adminBehaviorMapper.countCollections());
        vo.setTotalNotes(adminBehaviorMapper.countNotes());
        vo.setTotalAiChats(adminBehaviorMapper.countAiChats());

        // 取近 2 天数据推算今日 / 昨日，计算环比
        Map<String, Long> views = toDateMap(adminBehaviorMapper.viewTrend(2));
        Map<String, Long> collections = toDateMap(adminBehaviorMapper.collectionTrend(2));
        Map<String, Long> notes = toDateMap(adminBehaviorMapper.noteTrend(2));
        Map<String, Long> chats = toDateMap(adminBehaviorMapper.chatTrend(2));

        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();
        vo.setViewsTrend(growth(views.getOrDefault(today, 0L), views.getOrDefault(yesterday, 0L)));
        vo.setCollectionsTrend(growth(collections.getOrDefault(today, 0L), collections.getOrDefault(yesterday, 0L)));
        vo.setNotesTrend(growth(notes.getOrDefault(today, 0L), notes.getOrDefault(yesterday, 0L)));
        vo.setAiChatsTrend(growth(chats.getOrDefault(today, 0L), chats.getOrDefault(yesterday, 0L)));
        return vo;
    }

    @Override
    public BehaviorTrendVO trend(int days) {
        int span = Math.max(1, days);
        Map<String, Long> views = toDateMap(adminBehaviorMapper.viewTrend(span));
        Map<String, Long> collections = toDateMap(adminBehaviorMapper.collectionTrend(span));
        Map<String, Long> notes = toDateMap(adminBehaviorMapper.noteTrend(span));
        Map<String, Long> chats = toDateMap(adminBehaviorMapper.chatTrend(span));

        BehaviorTrendVO vo = new BehaviorTrendVO();
        List<String> dates = new ArrayList<>(span);
        List<Long> viewSeries = new ArrayList<>(span);
        List<Long> collectionSeries = new ArrayList<>(span);
        List<Long> noteSeries = new ArrayList<>(span);
        List<Long> chatSeries = new ArrayList<>(span);

        LocalDate today = LocalDate.now();
        for (int i = span - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String key = day.toString();
            dates.add(day.format(DATE_LABEL));
            viewSeries.add(views.getOrDefault(key, 0L));
            collectionSeries.add(collections.getOrDefault(key, 0L));
            noteSeries.add(notes.getOrDefault(key, 0L));
            chatSeries.add(chats.getOrDefault(key, 0L));
        }
        vo.setDates(dates);
        vo.setViews(viewSeries);
        vo.setCollections(collectionSeries);
        vo.setNotes(noteSeries);
        vo.setAiChats(chatSeries);
        return vo;
    }

    @Override
    public List<DistributionItemVO> distribution() {
        List<DistributionItemVO> list = new ArrayList<>(4);
        list.add(item("浏览", adminBehaviorMapper.countViews()));
        list.add(item("收藏", adminBehaviorMapper.countCollections()));
        list.add(item("笔记", adminBehaviorMapper.countNotes()));
        list.add(item("AI 对话", adminBehaviorMapper.countAiChats()));
        return list;
    }

    @Override
    public List<DistributionItemVO> hotScenic(int limit) {
        List<Map<String, Object>> rows = adminBehaviorMapper.hotScenicByView(Math.max(1, limit));
        List<DistributionItemVO> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            list.add(item(String.valueOf(row.get("name")), toLong(row.get("value"))));
        }
        return list;
    }

    @Override
    public List<ActiveUserVO> activeUsers(int limit) {
        List<Map<String, Object>> rows = adminBehaviorMapper.activeUsers(Math.max(1, limit));
        List<ActiveUserVO> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            ActiveUserVO vo = new ActiveUserVO();
            vo.setUserId(toLong(row.get("userId")));
            vo.setUsername(row.get("username") == null ? "未知用户" : row.get("username").toString());
            vo.setAvatar(row.get("avatar") == null ? null : row.get("avatar").toString());
            vo.setCount(toLong(row.get("cnt")));
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> recent(int limit) {
        List<Map<String, Object>> rows = adminBehaviorMapper.recentWithAvatar(Math.max(1, limit));
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("username", row.get("username"));
            item.put("avatar", row.get("avatar"));
            item.put("action", row.get("action"));
            item.put("target", row.get("target"));
            item.put("time", formatTime(row.get("time")));
            result.add(item);
        }
        return result;
    }

    /* ------------------------------ 内部辅助 ------------------------------ */

    private DistributionItemVO item(String name, long value) {
        DistributionItemVO vo = new DistributionItemVO();
        vo.setName(name);
        vo.setValue(value);
        return vo;
    }

    /** 把 [{date,count}] 转为 {date -> count} */
    private Map<String, Long> toDateMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Object date = row.get("date");
            if (date != null) {
                map.put(date.toString(), toLong(row.get("count")));
            }
        }
        return map;
    }

    /** 环比增长率（%） */
    private double growth(long today, long yesterday) {
        if (yesterday == 0) {
            return today == 0 ? 0.0 : GROWTH_BASE;
        }
        return Math.round((today - yesterday) * GROWTH_BASE / yesterday * 10) / 10.0;
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 时间格式化，兼容 LocalDateTime / Timestamp / 字符串 */
    private String formatTime(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(TIME_LABEL);
        }
        String text = value.toString();
        if (text.contains(".")) {
            text = text.substring(0, text.indexOf('.'));
        }
        return text.replace('T', ' ');
    }
}
