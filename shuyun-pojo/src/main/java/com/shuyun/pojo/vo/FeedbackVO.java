package com.shuyun.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 问题反馈 VO（管理端列表用，附带提交人用户名）
 */
@Data
public class FeedbackVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 反馈ID */
    private Long id;

    /** 提交用户ID */
    private Long userId;

    /** 提交人用户名 */
    private String username;

    /** 反馈类型 */
    private String type;

    /** 反馈内容 */
    private String content;

    /** 联系方式 */
    private String contact;

    /** 状态：0-待处理，1-已处理 */
    private Integer status;

    /** 管理员回复 */
    private String reply;

    /** 创建时间 */
    private LocalDateTime createTime;
}
