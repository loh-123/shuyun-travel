package com.shuyun.service;

import java.util.List;
import java.util.Map;

/**
 * 数据大屏业务接口
 *
 * <p>为「巴蜀文旅数据大屏」提供聚合数据，接口均为只读；部分维度为演示用合成数据。</p>
 */
public interface ScreenService {

    /**
     * 关键指标
     *
     * @return {userCount, visitCount, scenicCount, aiChatCount, foodCount, heritageCount, cultureCount,
     *         viewSum, userSpark, visitSpark, scenicSpark, chatSpark}
     */
    Map<String, Object> overview();

    /**
     * 文化热度排行（由 user_view_history + user_collection 按文化分类统计）
     *
     * @return [{name, value}]
     */
    List<Map<String, Object>> cultureRank();

    /**
     * 用户画像（年龄段 / 性别 / 地域分布）
     *
     * @return {total, age:[{name,value}], gender:[{name,value}], region:[{name,value}]}
     */
    Map<String, Object> userProfile();

    /**
     * 地图数据（21 市州）
     *
     * @return [{id, name, value, lng, lat, scenicCount, visitCount, foodCount, heritageCount}]
     */
    List<Map<String, Object>> mapData();

    /**
     * 实时数据流
     *
     * @param limit 条数
     * @return [{username, avatar, action, target, time}]
     */
    List<Map<String, Object>> realTime(int limit);

    /**
     * 近 24 小时访问趋势
     *
     * @return [{time, value}]
     */
    List<Map<String, Object>> visitTrend();

    /**
     * 热门景点 TOP N
     *
     * @param limit 数量
     * @return [{name, value}]
     */
    List<Map<String, Object>> hotScenic(int limit);

    /**
     * AI 对话词云
     *
     * @return [{name, value}]
     */
    List<Map<String, Object>> wordCloud();

    /**
     * 底部滚动通知
     *
     * @return 通知文案列表
     */
    List<String> notice();
}
