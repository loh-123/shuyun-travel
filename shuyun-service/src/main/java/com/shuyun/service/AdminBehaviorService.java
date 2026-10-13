package com.shuyun.service;

import com.shuyun.pojo.vo.ActiveUserVO;
import com.shuyun.pojo.vo.BehaviorStatsVO;
import com.shuyun.pojo.vo.BehaviorTrendVO;
import com.shuyun.pojo.vo.DistributionItemVO;

import java.util.List;
import java.util.Map;

/**
 * 后台用户行为统计业务接口
 */
public interface AdminBehaviorService {

    /**
     * 顶部统计卡片：总浏览量 / 总收藏数 / 总笔记数 / AI 问答数（含环比）
     *
     * @return 统计卡片数据
     */
    BehaviorStatsVO stats();

    /**
     * 近 N 天行为趋势（浏览 / 收藏 / 笔记 / AI 对话）
     *
     * @param days 天数
     * @return 趋势数据
     */
    BehaviorTrendVO trend(int days);

    /**
     * 功能使用占比（环形图）
     *
     * @return [{name, value}]
     */
    List<DistributionItemVO> distribution();

    /**
     * 热门浏览景点 TOP N
     *
     * @param limit 数量
     * @return [{name, value}]
     */
    List<DistributionItemVO> hotScenic(int limit);

    /**
     * 活跃用户 TOP N
     *
     * @param limit 数量
     * @return 活跃用户列表
     */
    List<ActiveUserVO> activeUsers(int limit);

    /**
     * 最近用户行为动态
     *
     * @param limit 数量
     * @return [{username, action, target, time}]
     */
    List<Map<String, Object>> recent(int limit);
}
