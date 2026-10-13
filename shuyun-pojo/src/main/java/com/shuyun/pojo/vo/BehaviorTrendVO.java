package com.shuyun.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 用户行为趋势 VO（近 N 天四条曲线）
 *
 * <p>以「日期 + 四个等长序列」的形式返回，便于 ECharts 直接消费。</p>
 */
@Data
public class BehaviorTrendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日期标签（MM-dd） */
    private List<String> dates;

    /** 浏览序列 */
    private List<Long> views;

    /** 收藏序列 */
    private List<Long> collections;

    /** 笔记序列 */
    private List<Long> notes;

    /** AI 对话序列 */
    private List<Long> aiChats;
}
