package com.shuyun.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 后台数据看板统计 Mapper
 *
 * <p>聚合查询，只读；均为一次性统计 SQL，直接以 {@code @Select} 注解定义，无需 XML。</p>
 */
public interface AdminStatMapper {

    /* ------------------------------------------------------------
     * 顶部统计卡片：累计值
     * ---------------------------------------------------------- */

    /** 用户总数 */
    @Select("SELECT COUNT(*) FROM `user` WHERE deleted = 0")
    long countUsers();

    /** 景点总数 */
    @Select("SELECT COUNT(*) FROM scenic_spot WHERE deleted = 0")
    long countScenics();

    /** AI 对话总数（仅统计用户提问） */
    @Select("SELECT COUNT(*) FROM user_ai_chat WHERE deleted = 0 AND role = 'user'")
    long countAiChats();

    /** 美食总数 */
    @Select("SELECT COUNT(*) FROM food WHERE deleted = 0")
    long countFoods();

    /** 非遗总数 */
    @Select("SELECT COUNT(*) FROM heritage WHERE deleted = 0")
    long countHeritages();

    /** 文化总数 */
    @Select("SELECT COUNT(*) FROM culture WHERE deleted = 0")
    long countCultures();

    /** 景点人气总和（累计浏览量，作为大屏「游客数」） */
    @Select("SELECT COALESCE(SUM(view_count), 0) FROM scenic_spot WHERE deleted = 0")
    long sumScenicViews();

    /* ------------------------------------------------------------
     * 顶部统计卡片：环比昨日（今日新增 vs 昨日新增）
     * ---------------------------------------------------------- */

    /** 今日新增用户 */
    @Select("SELECT COUNT(*) FROM `user` WHERE deleted = 0 AND DATE(create_time) = CURDATE()")
    long countNewUsersToday();

    /** 昨日新增用户 */
    @Select("SELECT COUNT(*) FROM `user` WHERE deleted = 0 AND DATE(create_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    long countNewUsersYesterday();

    /** 今日新增景点 */
    @Select("SELECT COUNT(*) FROM scenic_spot WHERE deleted = 0 AND DATE(create_time) = CURDATE()")
    long countNewScenicsToday();

    /** 昨日新增景点 */
    @Select("SELECT COUNT(*) FROM scenic_spot WHERE deleted = 0 AND DATE(create_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    long countNewScenicsYesterday();

    /** 今日访问量（浏览足迹数） */
    @Select("SELECT COUNT(*) FROM user_view_history WHERE deleted = 0 AND DATE(view_time) = CURDATE()")
    long countVisitsToday();

    /** 昨日访问量 */
    @Select("SELECT COUNT(*) FROM user_view_history WHERE deleted = 0 AND DATE(view_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    long countVisitsYesterday();

    /** 今日新增 AI 对话 */
    @Select("SELECT COUNT(*) FROM user_ai_chat WHERE deleted = 0 AND role = 'user' AND DATE(create_time) = CURDATE()")
    long countAiChatsToday();

    /** 昨日新增 AI 对话 */
    @Select("SELECT COUNT(*) FROM user_ai_chat WHERE deleted = 0 AND role = 'user' AND DATE(create_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    long countAiChatsYesterday();

    /* ------------------------------------------------------------
     * 图表
     * ---------------------------------------------------------- */

    /**
     * 近 N 天访问趋势
     *
     * @param days 天数
     * @return [{date, count}]
     */
    @Select("SELECT DATE(view_time) AS date, COUNT(*) AS count "
            + "FROM user_view_history "
            + "WHERE deleted = 0 AND view_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE(view_time) ORDER BY date")
    List<Map<String, Object>> visitTrend(@Param("days") int days);

    /**
     * 用户兴趣分布：按浏览过的文化条目所属分类聚合
     *
     * @return [{name, value}]
     */
    @Select("SELECT c.category AS name, COUNT(*) AS value "
            + "FROM user_view_history h "
            + "JOIN culture c ON h.target_type = 'CULTURE' AND h.target_id = c.id "
            + "WHERE h.deleted = 0 AND c.deleted = 0 "
            + "GROUP BY c.category ORDER BY value DESC")
    List<Map<String, Object>> interestDistribution();

    /**
     * 兴趣分布兜底：无浏览数据时，按文化条目的分类数量统计
     *
     * @return [{name, value}]
     */
    @Select("SELECT category AS name, COUNT(*) AS value FROM culture WHERE deleted = 0 GROUP BY category ORDER BY value DESC")
    List<Map<String, Object>> cultureCategoryDistribution();

    /**
     * 热门景点 TOP N（按人气 view_count）
     *
     * @param limit 数量
     * @return [{name, value}]
     */
    @Select("SELECT id AS id, name AS name, view_count AS value FROM scenic_spot WHERE deleted = 0 "
            + "ORDER BY view_count DESC LIMIT #{limit}")
    List<Map<String, Object>> hotScenic(@Param("limit") int limit);

    /**
     * 最近用户活动（浏览 / 收藏 / AI 对话 / 创建行程 合并，按时间倒序）
     *
     * @param limit 数量
     * @return [{username, action, target, time}]
     */
    @Select("SELECT u.username AS username, '浏览' AS action, h.target_name AS target, h.view_time AS time "
            + "FROM user_view_history h JOIN `user` u ON h.user_id = u.id WHERE h.deleted = 0 "
            + "UNION ALL "
            + "SELECT u.username AS username, '收藏' AS action, s.name AS target, col.create_time AS time "
            + "FROM user_collection col JOIN `user` u ON col.user_id = u.id "
            + "LEFT JOIN scenic_spot s ON col.target_type = 'SCENIC' AND col.target_id = s.id WHERE col.deleted = 0 "
            + "UNION ALL "
            + "SELECT u.username AS username, 'AI 对话' AS action, LEFT(a.content, 40) AS target, a.create_time AS time "
            + "FROM user_ai_chat a JOIN `user` u ON a.user_id = u.id WHERE a.deleted = 0 AND a.role = 'user' "
            + "UNION ALL "
            + "SELECT u.username AS username, '创建行程' AS action, t.title AS target, t.create_time AS time "
            + "FROM user_trip t JOIN `user` u ON t.user_id = u.id WHERE t.deleted = 0 "
            + "ORDER BY time DESC LIMIT #{limit}")
    List<Map<String, Object>> recentActivities(@Param("limit") int limit);

    /* ------------------------------------------------------------
     * 指标卡迷你走势（近 7 天，含补齐的零值日）
     * ---------------------------------------------------------- */

    /**
     * 近 7 天每日新增行程
     *
     * @return [{date, count}]
     */
    @Select("SELECT DATE(create_time) AS date, COUNT(*) AS count FROM user_trip "
            + "WHERE deleted = 0 AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) "
            + "GROUP BY DATE(create_time) ORDER BY date")
    List<Map<String, Object>> tripDailyTrend();

    /**
     * 近 7 天每日新增会话（按 session_id 去重）
     *
     * @return [{date, count}]
     */
    @Select("SELECT DATE(create_time) AS date, COUNT(DISTINCT session_id) AS count FROM user_ai_chat "
            + "WHERE deleted = 0 AND role = 'user' AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) "
            + "GROUP BY DATE(create_time) ORDER BY date")
    List<Map<String, Object>> chatDailyTrend();
}
