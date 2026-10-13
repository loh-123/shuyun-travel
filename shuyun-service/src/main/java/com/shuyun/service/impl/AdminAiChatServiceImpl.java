package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shuyun.common.BusinessException;
import com.shuyun.common.PageResult;
import com.shuyun.mapper.AdminStatMapper;
import com.shuyun.mapper.UserAiChatMapper;
import com.shuyun.pojo.entity.User;
import com.shuyun.pojo.entity.UserAiChat;
import com.shuyun.pojo.vo.AdminChatSessionVO;
import com.shuyun.service.AdminAiChatService;
import com.shuyun.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 后台 AI 对话管理业务实现
 *
 * <p>按 {@code session_id} 将逐条消息聚合为「会话」，并据此完成列表、详情、标记、导出。
 * 意图分类为基于关键词的轻量推断（数据量可控时足够使用）。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminAiChatServiceImpl implements AdminAiChatService {

    /** 角色：用户 */
    private static final String ROLE_USER = "user";

    /** 首条提问截断长度 */
    private static final int QUESTION_MAX = 30;

    /** 会话列表时间格式 */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserAiChatMapper userAiChatMapper;

    private final AdminStatMapper adminStatMapper;

    private final UserService userService;

    @Override
    public PageResult<AdminChatSessionVO> pageSessions(String keyword, String intent, String startDate, String endDate,
                                                       int page, int size) {
        List<SessionAgg> all = aggregate();

        String kw = keyword == null ? "" : keyword.trim();
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);

        List<SessionAgg> filtered = all.stream()
                .filter(s -> !StringUtils.hasText(intent) || intent.equals(s.intent))
                .filter(s -> !StringUtils.hasText(kw)
                        || (s.username != null && s.username.contains(kw))
                        || (s.firstQuestion != null && s.firstQuestion.contains(kw)))
                .filter(s -> start == null || (s.firstTime != null && !s.firstTime.toLocalDate().isBefore(start)))
                .filter(s -> end == null || (s.lastTime != null && !s.lastTime.toLocalDate().isAfter(end)))
                .sorted(Comparator.comparing((SessionAgg s) -> s.lastTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        long total = filtered.size();
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(filtered.size(), from + size);
        List<AdminChatSessionVO> records = from >= filtered.size()
                ? new ArrayList<>()
                : filtered.subList(from, to).stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(total, records, page, size);
    }

    @Override
    public Map<String, Object> getSessionDetail(String sessionId) {
        List<UserAiChat> messages = userAiChatMapper.selectList(Wrappers.<UserAiChat>lambdaQuery()
                .eq(UserAiChat::getSessionId, sessionId)
                .eq(UserAiChat::getDeleted, 0)
                .orderByAsc(UserAiChat::getId));
        if (messages.isEmpty()) {
            throw new BusinessException("会话不存在");
        }

        SessionAgg agg = aggregate().stream()
                .filter(s -> sessionId.equals(s.sessionId))
                .findFirst()
                .orElse(null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session", agg == null ? null : toVO(agg));
        data.put("messages", messages);
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mark(String sessionId, Integer markType) {
        userAiChatMapper.update(null, Wrappers.<UserAiChat>lambdaUpdate()
                .set(UserAiChat::getMarkType, markType == null ? 0 : markType)
                .eq(UserAiChat::getSessionId, sessionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSession(String sessionId) {
        userAiChatMapper.update(null, Wrappers.<UserAiChat>lambdaUpdate()
                .set(UserAiChat::getDeleted, 1)
                .eq(UserAiChat::getSessionId, sessionId));
    }

    @Override
    public List<Map<String, Object>> exportSessions(List<String> sessionIds) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (sessionIds == null || sessionIds.isEmpty()) {
            return result;
        }
        Map<String, SessionAgg> aggMap = aggregate().stream()
                .collect(Collectors.toMap(s -> s.sessionId, s -> s, (a, b) -> a));
        for (String sessionId : sessionIds) {
            List<UserAiChat> messages = userAiChatMapper.selectList(Wrappers.<UserAiChat>lambdaQuery()
                    .eq(UserAiChat::getSessionId, sessionId)
                    .eq(UserAiChat::getDeleted, 0)
                    .orderByAsc(UserAiChat::getId));
            if (messages.isEmpty()) {
                continue;
            }
            SessionAgg agg = aggMap.get(sessionId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sessionId", sessionId);
            item.put("username", agg == null ? null : agg.username);
            item.put("intent", agg == null ? "其他" : agg.intent);
            item.put("firstQuestion", agg == null ? "" : agg.firstQuestion);

            List<Map<String, Object>> msgs = new ArrayList<>();
            for (UserAiChat m : messages) {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("role", m.getRole());
                msg.put("content", m.getContent());
                msg.put("time", m.getCreateTime() == null ? "" : DATE_TIME.format(m.getCreateTime()));
                msgs.add(msg);
            }
            item.put("messages", msgs);
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> stats() {
        List<SessionAgg> all = aggregate();
        long total = all.size();
        LocalDate today = LocalDate.now();
        long todayCount = all.stream()
                .filter(s -> s.firstTime != null && today.equals(s.firstTime.toLocalDate()))
                .count();
        double avgRounds = all.isEmpty() ? 0.0
                : all.stream().mapToInt(s -> s.userMessageCount).average().orElse(0.0);
        long quality = all.stream().filter(s -> Integer.valueOf(1).equals(s.markType)).count();
        double satisfaction = total == 0 ? 0.0 : (quality * 100.0 / total);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("today", todayCount);
        data.put("avgRounds", Math.round(avgRounds * 10) / 10.0);
        data.put("quality", quality);
        data.put("satisfaction", Math.round(satisfaction * 10) / 10.0);
        data.put("spark", recent7Days(adminStatMapper.chatDailyTrend()));
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

    /* ------------------------------ 内部辅助 ------------------------------ */

    /**
     * 加载全部消息并按会话聚合
     */
    private List<SessionAgg> aggregate() {
        List<UserAiChat> all = userAiChatMapper.selectList(Wrappers.<UserAiChat>lambdaQuery()
                .eq(UserAiChat::getDeleted, 0)
                .orderByAsc(UserAiChat::getId));
        Map<String, List<UserAiChat>> grouped = all.stream()
                .filter(c -> c.getSessionId() != null)
                .collect(Collectors.groupingBy(UserAiChat::getSessionId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, User> userMap = new HashMap<>();
        List<Long> userIds = all.stream().map(UserAiChat::getUserId).filter(Objects::nonNull).distinct().toList();
        if (!userIds.isEmpty()) {
            userService.listByIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        List<SessionAgg> sessions = new ArrayList<>();
        grouped.forEach((sessionId, list) -> {
            SessionAgg agg = new SessionAgg();
            agg.sessionId = sessionId;
            List<UserAiChat> userMsgs = list.stream().filter(c -> ROLE_USER.equals(c.getRole())).toList();
            agg.userId = list.get(0).getUserId();
            User owner = userMap.get(agg.userId);
            agg.username = owner == null ? "未知用户" : owner.getUsername();
            agg.avatar = owner == null ? null : owner.getAvatar();
            agg.messageCount = list.size();
            agg.userMessageCount = userMsgs.size();
            agg.firstTime = list.get(0).getCreateTime();
            agg.lastTime = list.get(list.size() - 1).getCreateTime();
            agg.markType = list.stream().map(UserAiChat::getMarkType).filter(Objects::nonNull)
                    .max(Integer::compareTo).orElse(0);
            String question = userMsgs.isEmpty() ? "（无提问）" : String.valueOf(userMsgs.get(0).getContent());
            agg.firstQuestion = question.length() > QUESTION_MAX
                    ? question.substring(0, QUESTION_MAX) + "…" : question;
            agg.intent = classifyIntent(userMsgs.isEmpty() ? "" : String.valueOf(userMsgs.get(0).getContent()));
            agg.durationSeconds = (agg.firstTime != null && agg.lastTime != null)
                    ? Math.max(0, Duration.between(agg.firstTime, agg.lastTime).getSeconds()) : 0L;
            sessions.add(agg);
        });
        return sessions;
    }

    /** 会话聚合内部结构 */
    private static class SessionAgg {
        String sessionId;
        Long userId;
        String username;
        String avatar;
        String firstQuestion;
        Integer messageCount = 0;
        int userMessageCount = 0;
        Long durationSeconds = 0L;
        String intent = "其他";
        Integer markType = 0;
        LocalDateTime firstTime;
        LocalDateTime lastTime;
    }

    /** 聚合结构转 VO */
    private AdminChatSessionVO toVO(SessionAgg agg) {
        AdminChatSessionVO vo = new AdminChatSessionVO();
        vo.setSessionId(agg.sessionId);
        vo.setUserId(agg.userId);
        vo.setUsername(agg.username);
        vo.setAvatar(agg.avatar);
        vo.setFirstQuestion(agg.firstQuestion);
        vo.setMessageCount(agg.messageCount);
        vo.setDurationSeconds(agg.durationSeconds);
        vo.setIntent(agg.intent);
        vo.setMarkType(agg.markType);
        vo.setLastTime(agg.lastTime);
        return vo;
    }

    /**
     * 基于关键词的意图分类
     */
    private String classifyIntent(String question) {
        String q = question == null ? "" : question;
        if (hit(q, "行程", "规划", "路线", "几日", "几天", "安排", "日程", "三日", "两日")) {
            return "行程规划";
        }
        if (hit(q, "文化", "川剧", "蜀绣", "三星堆", "三国", "茶马", "非遗", "青铜", "变脸", "历史")) {
            return "文化科普";
        }
        if (hit(q, "天气", "下雨", "气温", "穿什么", "季节", "气候", "温度")) {
            return "天气关怀";
        }
        if (hit(q, "美食", "川菜", "吃", "小吃", "火锅", "味型", "推荐菜")) {
            return "美食推荐";
        }
        return "其他";
    }

    /** 关键词命中 */
    private boolean hit(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /** 解析日期（yyyy-MM-dd），失败返回 null */
    private LocalDate parseDate(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }
}
