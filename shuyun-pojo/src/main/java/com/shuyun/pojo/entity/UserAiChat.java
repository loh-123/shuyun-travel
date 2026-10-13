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
 * AI 对话记录
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_ai_chat")
public class UserAiChat implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 会话ID */
    @TableField("session_id")
    private String sessionId;

    /** 角色：user/assistant */
    @TableField("role")
    private String role;

    /** 消息内容 */
    @TableField("content")
    private String content;

    /** 消耗 token 数 */
    @TableField("tokens")
    private Integer tokens;

    /** 标记类型：0-未标记，1-优质对话，2-训练语料 */
    @TableField("mark_type")
    private Integer markType;

    /** 输入长度（字符数，仅 AI 回复记录填写） */
    @TableField("input_len")
    private Integer inputLen;

    /** 输出长度（字符数，仅 AI 回复记录填写） */
    @TableField("output_len")
    private Integer outputLen;

    /** 本次调用耗时（毫秒） */
    @TableField("duration_ms")
    private Long durationMs;

    /** 是否成功：0-失败，1-成功 */
    @TableField("success")
    private Integer success;

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
