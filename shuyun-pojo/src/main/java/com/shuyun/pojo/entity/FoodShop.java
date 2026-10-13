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
 * 美食店铺
 *
 * <p>供 AI 工具 {@code recommendFood} 按城市 / 口味查询推荐店铺。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("food_shop")
public class FoodShop implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺名称 */
    @TableField("name")
    private String name;

    /** 所属城市 */
    @TableField("city")
    private String city;

    /** 口味 / 类型（如 麻辣、火锅、小吃） */
    @TableField("taste")
    private String taste;

    /** 地址 */
    @TableField("address")
    private String address;

    /** 人均价格（元） */
    @TableField("price")
    private BigDecimal price;

    /** 评分（0-5） */
    @TableField("rating")
    private BigDecimal rating;

    /** 简介 */
    @TableField("description")
    private String description;

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
