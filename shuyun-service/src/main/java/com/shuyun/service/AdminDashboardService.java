package com.shuyun.service;

import java.util.List;
import java.util.Map;

/**
 * 后台数据看板业务接口
 */
public interface AdminDashboardService {

    /**
     * 顶部统计卡片数据（含环比昨日趋势）
     *
     * @return {users:{value,trend}, scenics:{value,trend}, visits:{value,trend}, chats:{value,trend}}
     */
    Map<String, Object> stats();

    /**
     * 近 N 天访问趋势（自动补全空缺日期）
     *
     * @param days 天数
     * @return [{date, fullDate, count}]
     */
    List<Map<String, Object>> visitTrend(int days);

    /**
     * 用户兴趣分布（按文化分类）
     *
     * @return [{name, value}]
     */
    List<Map<String, Object>> interestDistribution();

    /**
     * 热门景点 TOP N
     *
     * @param limit 数量
     * @return [{name, value}]
     */
    List<Map<String, Object>> hotScenic(int limit);

    /**
     * 最近用户活动
     *
     * @param limit 数量
     * @return [{username, action, target, time}]
     */
    List<Map<String, Object>> recentActivities(int limit);
}
