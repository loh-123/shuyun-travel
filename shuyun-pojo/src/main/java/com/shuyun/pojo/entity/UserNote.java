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
 * 旅行笔记
 *
 * <p>可自由撰写，也可关联某个景点 / 文化条目（{@code targetName} 允许自由填写）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_note")
public class UserNote implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 标题 */
    @TableField("title")
    private String title;

    /** 正文 */
    @TableField("content")
    private String content;

    /** 关联对象类型：SCENIC / CULTURE（可空） */
    @TableField("target_type")
    private String targetType;

    /** 关联对象ID（可空） */
    @TableField("target_id")
    private Long targetId;

    /** 关联对象名称（可自由填写） */
    @TableField("target_name")
    private String targetName;

    /** 标签（逗号分隔） */
    @TableField("tags")
    private String tags;

    /** 审核状态：0-待审核，1-已通过，2-已拒绝 */
    @TableField("audit_status")
    private Integer auditStatus;

    /** 拒绝理由（审核不通过时填写） */
    @TableField("reject_reason")
    private String rejectReason;

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
