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
 * 用户行程
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_trip")
public class UserTrip implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 行程标题 */
    @TableField("title")
    private String title;

    /** 行程天数 */
    @TableField("days")
    private Integer days;

    /** 同行人 */
    @TableField("companion")
    private String companion;

    /** 兴趣偏好 */
    @TableField("interests")
    private String interests;

    /** 预算 */
    @TableField("budget")
    private BigDecimal budget;

    /** 状态：0-草稿，1-正常 */
    @TableField("status")
    private Integer status;

    /** 封面图 */
    @TableField("cover_image")
    private String coverImage;

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
