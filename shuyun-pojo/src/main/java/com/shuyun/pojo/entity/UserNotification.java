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
 * 站内信 / 用户通知
 *
 * <p>后台审核笔记拒绝等操作时写入，用户可在用户后台查看。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_notification")
public class UserNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收用户ID */
    @TableField("user_id")
    private Long userId;

    /** 标题 */
    @TableField("title")
    private String title;

    /** 内容 */
    @TableField("content")
    private String content;

    /** 类型：NOTE_AUDIT / SYSTEM 等 */
    @TableField("type")
    private String type;

    /** 是否已读：0-未读，1-已读 */
    @TableField("is_read")
    private Integer isRead;

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
