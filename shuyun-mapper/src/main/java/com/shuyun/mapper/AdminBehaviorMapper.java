package com.shuyun.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 用户行为统计 Mapper
 *
 * <p>聚合查询，只读；以 {@code @Select} 注解定义，无需 XML。</p>
 */
public interface AdminBehaviorMapper {

    /* ------------------------------ 累计值 ------------------------------ */

    /** 总浏览量 */
    @Select("SELECT COUNT(*) FROM user_view_history WHERE deleted = 0")
    long countViews();

    /** 总收藏数 */
    @Select("SELECT COUNT(*) FROM user_collection WHERE deleted = 0")
    long countCollections();

    /** 总笔记数 */
    @Select("SELECT COUNT(*) FROM user_note WHERE deleted = 0")
    long countNotes();

    /** AI 问答数（仅用户提问） */
    @Select("SELECT COUNT(*) FROM user_ai_chat WHERE deleted = 0 AND role = 'user'")
    long countAiChats();

    /* ------------------------------ 近 N 天趋势 ------------------------------ */

    /**
     * 浏览趋势
     *
     * @param days 天数
     * @return [{date, count}]
     */
    @Select("SELECT DATE(view_time) AS date, COUNT(*) AS count FROM user_view_history "
            + "WHERE deleted = 0 AND view_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE(view_time)")
    List<Map<String, Object>> viewTrend(@Param("days") int days);

    /** 收藏趋势 */
    @Select("SELECT DATE(create_time) AS date, COUNT(*) AS count FROM user_collection "
            + "WHERE deleted = 0 AND create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE(create_time)")
    List<Map<String, Object>> collectionTrend(@Param("days") int days);

    /** 笔记趋势 */
    @Select("SELECT DATE(create_time) AS date, COUNT(*) AS count FROM user_note "
            + "WHERE deleted = 0 AND create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE(create_time)")
    List<Map<String, Object>> noteTrend(@Param("days") int days);

    /** AI 对话趋势 */
    @Select("SELECT DATE(create_time) AS date, COUNT(*) AS count FROM user_ai_chat "
            + "WHERE deleted = 0 AND role = 'user' AND create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE(create_time)")
    List<Map<String, Object>> chatTrend(@Param("days") int days);

    /* ------------------------------ 榜单 ------------------------------ */

    /**
     * 热门浏览景点 TOP N（按浏览足迹次数）
     *
     * @param limit 数量
     * @return [{name, value}]
     */
    @Select("SELECT h.target_name AS name, COUNT(*) AS value FROM user_view_history h "
            + "WHERE h.deleted = 0 AND h.target_type = 'SCENIC' AND h.target_name IS NOT NULL "
            + "GROUP BY h.target_name ORDER BY value DESC LIMIT #{limit}")
    List<Map<String, Object>> hotScenicByView(@Param("limit") int limit);

    /**
     * 活跃用户 TOP N（浏览 + 收藏 + 笔记 + AI 提问 合计）
     *
     * @param limit 数量
     * @return [{userId, username, avatar, cnt}]
     */
    @Select("SELECT u.id AS userId, u.username AS username, u.avatar AS avatar, COUNT(*) AS cnt "
            + "FROM ("
            + "  SELECT user_id FROM user_view_history WHERE deleted = 0 "
            + "  UNION ALL SELECT user_id FROM user_collection WHERE deleted = 0 "
            + "  UNION ALL SELECT user_id FROM user_note WHERE deleted = 0 "
            + "  UNION ALL SELECT user_id FROM user_ai_chat WHERE deleted = 0 AND role = 'user'"
            + ") a JOIN `user` u ON a.user_id = u.id "
            + "GROUP BY u.id, u.username, u.avatar ORDER BY cnt DESC LIMIT #{limit}")
    List<Map<String, Object>> activeUsers(@Param("limit") int limit);

    /**
     * 最近用户行为动态（带头像，按时间倒序）
     *
     * @param limit 数量
     * @return [{username, avatar, action, target, time}]
     */
    @Select("SELECT u.username AS username, u.avatar AS avatar, '浏览' AS action, h.target_name AS target, h.view_time AS time "
            + "FROM user_view_history h JOIN `user` u ON h.user_id = u.id WHERE h.deleted = 0 "
            + "UNION ALL "
            + "SELECT u.username, u.avatar, '收藏', s.name, col.create_time "
            + "FROM user_collection col JOIN `user` u ON col.user_id = u.id "
            + "LEFT JOIN scenic_spot s ON col.target_type = 'SCENIC' AND col.target_id = s.id WHERE col.deleted = 0 "
            + "UNION ALL "
            + "SELECT u.username, u.avatar, 'AI 对话', LEFT(a.content, 40), a.create_time "
            + "FROM user_ai_chat a JOIN `user` u ON a.user_id = u.id WHERE a.deleted = 0 AND a.role = 'user' "
            + "UNION ALL "
            + "SELECT u.username, u.avatar, '创建行程', t.title, t.create_time "
            + "FROM user_trip t JOIN `user` u ON t.user_id = u.id WHERE t.deleted = 0 "
            + "ORDER BY time DESC LIMIT #{limit}")
    List<Map<String, Object>> recentWithAvatar(@Param("limit") int limit);
}
