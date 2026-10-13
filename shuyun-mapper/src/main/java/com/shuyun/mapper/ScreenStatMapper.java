package com.shuyun.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 数据大屏统计 Mapper
 *
 * <p>聚合查询，只读；以 {@code @Select} 注解定义，无需 XML。</p>
 */
public interface ScreenStatMapper {

    /**
     * 各市州景点数
     *
     * @return [{cityId, cnt}]
     */
    @Select("SELECT city_id AS cityId, COUNT(*) AS cnt FROM scenic_spot "
            + "WHERE deleted = 0 AND city_id IS NOT NULL GROUP BY city_id")
    List<Map<String, Object>> cityScenicCounts();

    /**
     * 各市州访问量（按景点浏览足迹归属市州统计）
     *
     * @return [{cityId, cnt}]
     */
    @Select("SELECT s.city_id AS cityId, COUNT(*) AS cnt "
            + "FROM user_view_history h JOIN scenic_spot s ON h.target_type = 'SCENIC' AND h.target_id = s.id "
            + "WHERE h.deleted = 0 AND s.deleted = 0 AND s.city_id IS NOT NULL GROUP BY s.city_id")
    List<Map<String, Object>> cityVisitCounts();

    /**
     * 各市州美食数（food 表以 city_name 归属市州）
     *
     * @return [{name, cnt}]
     */
    @Select("SELECT city_name AS name, COUNT(*) AS cnt FROM food "
            + "WHERE deleted = 0 AND city_name IS NOT NULL GROUP BY city_name")
    List<Map<String, Object>> cityFoodCounts();

    /**
     * 文化各分类的浏览量
     *
     * @return [{name, value}]
     */
    @Select("SELECT c.category AS name, COUNT(*) AS value "
            + "FROM user_view_history h JOIN culture c ON h.target_type = 'CULTURE' AND h.target_id = c.id "
            + "WHERE h.deleted = 0 AND c.deleted = 0 GROUP BY c.category")
    List<Map<String, Object>> cultureViewCounts();

    /**
     * 文化各分类的收藏量
     *
     * @return [{name, value}]
     */
    @Select("SELECT c.category AS name, COUNT(*) AS value "
            + "FROM user_collection col JOIN culture c ON col.target_type = 'CULTURE' AND col.target_id = c.id "
            + "WHERE col.deleted = 0 AND c.deleted = 0 GROUP BY c.category")
    List<Map<String, Object>> cultureCollectionCounts();

    /**
     * 近 24 小时按小时聚合的访问量
     *
     * @return [{hour, cnt}]
     */
    @Select("SELECT HOUR(view_time) AS hour, COUNT(*) AS cnt FROM user_view_history "
            + "WHERE deleted = 0 AND view_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) GROUP BY HOUR(view_time)")
    List<Map<String, Object>> hourlyVisits();

    /**
     * 最近的用户提问内容（用于词云统计）
     *
     * @param limit 条数
     * @return 内容列表
     */
    @Select("SELECT content FROM user_ai_chat WHERE deleted = 0 AND role = 'user' "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<String> recentUserMessages(@Param("limit") int limit);
}
