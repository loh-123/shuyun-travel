package com.shuyun.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户行为统计卡片 VO
 *
 * <p>总计值 + 各自较昨日的环比百分比，供顶部 4 张统计卡片展示。</p>
 */
@Data
public class BehaviorStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总浏览量 */
    private Long totalViews;

    /** 总收藏数 */
    private Long totalCollections;

    /** 总笔记数 */
    private Long totalNotes;

    /** AI 问答数 */
    private Long totalAiChats;

    /** 浏览量环比昨日（%） */
    private Double viewsTrend;

    /** 收藏数环比昨日（%） */
    private Double collectionsTrend;

    /** 笔记数环比昨日（%） */
    private Double notesTrend;

    /** AI 问答环比昨日（%） */
    private Double aiChatsTrend;
}
