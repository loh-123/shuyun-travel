package com.shuyun.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 行程每日明细
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_trip_day")
public class UserTripDay implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 行程ID */
    @TableField("trip_id")
    private Long tripId;

    /** 第几天 */
    @TableField("day_index")
    private Integer dayIndex;

    /** 日期（date 与 MySQL 类型关键字同名，列名用反引号转义以确保兼容） */
    @TableField("`date`")
    private LocalDate date;

    /** 景点ID */
    @TableField("scenic_id")
    private Long scenicId;

    /** 景点名称 */
    @TableField("scenic_name")
    private String scenicName;

    /** 停留时长 */
    @TableField("stay_duration")
    private String stayDuration;

    /** 交通方式 */
    @TableField("transport")
    private String transport;

    /** 美食推荐 */
    @TableField("food")
    private String food;

    /** 温馨提示 */
    @TableField("tips")
    private String tips;

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
