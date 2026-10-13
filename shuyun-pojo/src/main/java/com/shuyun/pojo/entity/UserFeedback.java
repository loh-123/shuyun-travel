package com.shuyun.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 问题反馈
 *
 * <p>用户在「用户后台 → 问题反馈」提交，管理员在「后台管理 → 问题反馈」查看与处理。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_feedback")
public class UserFeedback implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提交用户ID */
    @TableField("user_id")
    private Long userId;

    /** 反馈类型 */
    @TableField("type")
    private String type;

    /** 反馈内容 */
    @TableField("content")
    private String content;

    /** 联系方式（可选） */
    @TableField("contact")
    private String contact;

    /** 状态：0-待处理，1-已处理 */
    @TableField("status")
    private Integer status;

    /** 管理员回复 */
    @TableField("reply")
    private String reply;

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
