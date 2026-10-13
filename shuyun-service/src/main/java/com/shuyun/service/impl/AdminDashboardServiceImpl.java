package com.shuyun.service.impl;

import com.shuyun.mapper.AdminStatMapper;
import com.shuyun.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台数据看板业务实现
 *
 * <p>聚合 {@link AdminStatMapper} 的统计结果，补齐缺失日期、计算环比趋势。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    /** 趋势展示保留的百分比 */
    private static final double GROWTH_BASE = 100.0;

    /** 图表日期显示格式 */
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    /** 时间轴时间显示格式 */
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminStatMapper adminStatMapper;

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users", card(adminStatMapper.countUsers(),
                adminStatMapper.countNewUsersToday(), adminStatMapper.countNewUsersYesterday()));
        data.put("scenics", card(adminStatMapper.countScenics(),
                adminStatMapper.countNewScenicsToday(), adminStatMapper.countNewScenicsYesterday()));
        data.put("visits", card(adminStatMapper.countVisitsToday(),
                adminStatMapper.countVisitsToday(), adminStatMapper.countVisitsYesterday()));
        data.put("chats", card(adminStatMapper.countAiChats(),
                adminStatMapper.countAiChatsToday(), adminStatMapper.countAiChatsYesterday()));
        return data;
    }

    @Override
    public List<Map<String, Object>> visitTrend(int days) {
        int span = Math.max(1, days);
        List<Map<String, Object>> rows = adminStatMapper.visitTrend(span);

        // 先把已有数据按日期归到 map，便于补全空缺
        Map<String, Long> byDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object date = row.get("date");
            if (date == null) {
                continue;
            }
            byDate.merge(date.toString(), toLong(row.get("count")), Long::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>(span);
        LocalDate today = LocalDate.now();
        for (int i = span - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.format(DATE_LABEL));
            item.put("fullDate", day.toString());
            item.put("count", byDate.getOrDefault(day.toString(), 0L));
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> interestDistribution() {
        List<Map<String, Object>> rows = adminStatMapper.interestDistribution();
        if (rows == null || rows.isEmpty()) {
            // 暂无浏览数据时，退化为按文化条目的分类数量展示
            rows = adminStatMapper.cultureCategoryDistribution();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", row.get("name"));
            item.put("value", toLong(row.get("value")));
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> hotScenic(int limit) {
        List<Map<String, Object>> rows = adminStatMapper.hotScenic(Math.max(1, limit));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", toLong(row.get("id")));
            item.put("name", row.get("name"));
            item.put("value", toLong(row.get("value")));
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> recentActivities(int limit) {
        List<Map<String, Object>> rows = adminStatMapper.recentActivities(Math.max(1, limit));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("username", row.get("username"));
            item.put("action", row.get("action"));
            item.put("target", row.get("target"));
            item.put("time", formatTime(row.get("time")));
            result.add(item);
        }
        return result;
    }

    /**
     * 组装一张统计卡片：{value, trend}
     *
     * @param value     累计值
     * @param today     今日新增
     * @param yesterday 昨日新增
     */
    private Map<String, Object> card(long value, long today, long yesterday) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("value", value);
        card.put("trend", growth(today, yesterday));
        return card;
    }

    /**
     * 计算环比昨日增长率（%）
     */
    private double growth(long today, long yesterday) {
        if (yesterday == 0) {
            return today == 0 ? 0.0 : GROWTH_BASE;
        }
        double rate = (today - yesterday) * GROWTH_BASE / yesterday;
        return Math.round(rate * 10) / 10.0;
    }

    /**
     * 数值安全转换
     */
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

    /**
     * 时间格式化：兼容 LocalDateTime / Timestamp / 字符串
     */
    private String formatTime(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(TIME_LABEL);
        }
        String text = value.toString();
        // java.sql.Timestamp.toString() 形如 2026-09-19 10:20:30.0，去掉毫秒
        if (text.contains(".")) {
            text = text.substring(0, text.indexOf('.'));
        }
        return text.replace('T', ' ');
    }
}
