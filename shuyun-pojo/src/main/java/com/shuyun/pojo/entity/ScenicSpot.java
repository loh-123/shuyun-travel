package com.shuyun.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 景点
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("scenic_spot")
public class ScenicSpot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 景点名称 */
    @TableField("name")
    private String name;

    /** 所属市州ID */
    @TableField("city_id")
    private Long cityId;

    /** 景点类型 */
    @TableField("type")
    private String type;

    /** 景区等级 */
    @TableField("level")
    private String level;

    /** 地址 */
    @TableField("address")
    private String address;

    /** 经度 */
    @TableField("longitude")
    private BigDecimal longitude;

    /** 纬度 */
    @TableField("latitude")
    private BigDecimal latitude;

    /** 封面图 */
    @TableField("cover_image")
    private String coverImage;

    /** 景点介绍 */
    @TableField("description")
    private String description;

    /** 开放时间 */
    @TableField("open_time")
    private String openTime;

    /** 门票信息 */
    @TableField("ticket_info")
    private String ticketInfo;

    /** 评分 */
    @TableField("score")
    private BigDecimal score;

    /** 标签（逗号分隔） */
    @TableField("tags")
    private String tags;

    /** 浏览次数（人气） */
    @TableField("view_count")
    private Integer viewCount;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 逻辑删除：0-未删除，1-已删除 */
    @TableField("deleted")
    private Integer deleted;
}
