package com.shuyun.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 美食列表项 VO
 */
@Data
public class FoodVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 美食ID */
    private Long id;

    /** 名称 */
    private String name;

    /** 分类 */
    private String category;

    /** 所属城市 */
    private String cityName;

    /** 封面图 */
    private String coverImage;

    /** 简介 */
    private String description;

    /** 辣度（1-5） */
    private Integer spicyLevel;

    /** 人均价格（元） */
    private BigDecimal avgPrice;

    /** 代表店铺 */
    private String shopName;

    /** 店铺地址（供城市 3D 页面按区县归集） */
    private String shopAddress;

    /** 店铺经度（供城市 3D 页面 POI 打点） */
    private java.math.BigDecimal shopLongitude;

    /** 店铺纬度（供城市 3D 页面 POI 打点） */
    private java.math.BigDecimal shopLatitude;
}
