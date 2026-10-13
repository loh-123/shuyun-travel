package com.shuyun.pojo.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 景点新增 / 编辑入参
 *
 * <p>供后台「景点管理」抽屉表单提交使用，字段与 {@code scenic_spot} 表基本一一对应，
 * 额外携带详情图列表 {@link #images}（写入 {@code scenic_image} 表）。</p>
 */
@Data
public class ScenicSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（新增时为空） */
    private Long id;

    /** 景点名称 */
    private String name;

    /** 所属市州ID */
    private Long cityId;

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

    /** 标签（逗号分隔） */
    private String tags;

    /** 推荐理由 */
    private String recommendReason;

    /** 详情图地址列表（全量覆盖） */
    private List<String> images;
}
