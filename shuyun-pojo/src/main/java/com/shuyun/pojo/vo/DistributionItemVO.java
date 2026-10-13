package com.shuyun.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用「名称-数值」统计项 VO
 *
 * <p>用于功能使用占比环形图、热门景点柱状图等图表数据。</p>
 */
@Data
public class DistributionItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 名称 */
    private String name;

    /** 数值 */
    private Long value;
}
