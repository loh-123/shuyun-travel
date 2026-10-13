package com.shuyun.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 活跃用户 TOP VO
 */
@Data
public class ActiveUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 头像 */
    private String avatar;

    /** 行为总数 */
    private Long count;
}
