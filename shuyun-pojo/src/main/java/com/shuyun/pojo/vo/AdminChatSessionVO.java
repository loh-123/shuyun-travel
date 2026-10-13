package com.shuyun.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台 AI 会话 VO
 *
 * <p>按 session_id 聚合后的会话概要，供列表展示。</p>
 */
@Data
public class AdminChatSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID */
    private String sessionId;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 用户头像 */
    private String avatar;

    /** 首条提问（截断前 30 字） */
    private String firstQuestion;

    /** 消息数 */
    private Integer messageCount;

    /** 对话时长（秒） */
    private Long durationSeconds;

    /** 意图分类：行程规划 / 文化科普 / 天气关怀 / 美食推荐 / 其他 */
    private String intent;

    /** 标记类型：0-未标记，1-优质对话，2-训练语料 */
    private Integer markType;

    /** 最后消息时间 */
    private LocalDateTime lastTime;
}
