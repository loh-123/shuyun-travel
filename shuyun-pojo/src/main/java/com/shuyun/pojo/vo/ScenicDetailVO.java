package com.shuyun.pojo.vo;

import com.shuyun.pojo.entity.ScenicImage;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 景点详情 VO
 *
 * <p>封装景点基础信息 + 图片列表 + 同城市推荐。</p>
 */
@Data
public class ScenicDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 景点ID */
    private Long id;

    /** 景点名称 */
    private String name;

    /** 所属市州ID */
    private Long cityId;

    /** 所属市州名称 */
    private String cityName;

    /** 景点类型 */
    private String type;

    /** 景区等级 */
    private String level;

    /** 地址 */
    private String address;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 封面图 */
    private String coverImage;

    /** 景点介绍 */
    private String description;

    /** 开放时间 */
    private String openTime;

    /** 门票信息 */
    private String ticketInfo;

    /** 评分 */
    private BigDecimal score;

    /** 标签 */
    private String tags;

    /** 图片列表 */
    private List<ScenicImage> images;

    /** 同城市推荐（最多 3 条） */
    private List<ScenicSpotVO> recommends;
}
