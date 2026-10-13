package com.shuyun.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 景点列表项 VO
 */
@Data
public class ScenicSpotVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 景点ID */
    private Long id;

    /** 景点名称 */
    private String name;

    /** 所属市州名称 */
    private String cityName;

    /** 景点类型 */
    private String type;

    /** 景区等级 */
    private String level;

    /** 封面图 */
    private String coverImage;

    /** 简介 */
    private String description;

    /** 评分 */
    private BigDecimal score;

    /** 浏览次数（人气） */
    private Integer viewCount;

    /** 标签 */
    private String tags;

    /** 推荐理由 */
    private String recommendReason;

    /** 地址（供城市 3D 页面按区县归集） */
    private String address;

    /** 经度（供城市 3D 页面 POI 打点） */
    private java.math.BigDecimal longitude;

    /** 纬度（供城市 3D 页面 POI 打点） */
    private java.math.BigDecimal latitude;
}
