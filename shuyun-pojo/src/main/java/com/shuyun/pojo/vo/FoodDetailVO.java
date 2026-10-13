package com.shuyun.pojo.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 美食详情 VO
 *
 * <p>在 {@link FoodVO} 基础上补充视频、历史故事、推荐指数与店铺信息。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FoodDetailVO extends FoodVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 视频地址 */
    private String videoUrl;

    /** 历史故事 */
    private String history;

    /** 推荐指数（1-5） */
    private Integer recommendIndex;

    /** 店铺地址 */
    private String shopAddress;

    /** 店铺经度 */
    private BigDecimal shopLongitude;

    /** 店铺纬度 */
    private BigDecimal shopLatitude;

    /** 店铺图片 */
    private String shopImage;
}
