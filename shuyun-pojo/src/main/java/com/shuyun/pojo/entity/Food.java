package com.shuyun.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 美食
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("food")
public class Food implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 美食名称 */
    @TableField("name")
    private String name;

    /** 视频地址 */
    @TableField("video_url")
    private String videoUrl;

    /** 分类 */
    @TableField("category")
    private String category;

    /** 所属城市 */
    @TableField("city_name")
    private String cityName;

    /** 封面图 */
    @TableField("cover_image")
    private String coverImage;

    /** 简介 */
    @TableField("description")
    private String description;

    /** 历史故事 */
    @TableField("history")
    private String history;

    /** 辣度（1-5） */
    @TableField("spicy_level")
    private Integer spicyLevel;

    /** 推荐指数（1-5） */
    @TableField("recommend_index")
    private Integer recommendIndex;

    /** 人均价格（元） */
    @TableField("avg_price")
    private BigDecimal avgPrice;

    /** 代表店铺 */
    @TableField("shop_name")
    private String shopName;

    /** 店铺地址 */
    @TableField("shop_address")
    private String shopAddress;

    /** 店铺经度 */
    @TableField("shop_longitude")
    private BigDecimal shopLongitude;

    /** 店铺纬度 */
    @TableField("shop_latitude")
    private BigDecimal shopLatitude;

    /** 店铺图片 */
    @TableField("shop_image")
    private String shopImage;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 逻辑删除：0-未删除，1-已删除 */
    @TableField("deleted")
    private Integer deleted;
}
