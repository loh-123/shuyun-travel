package com.shuyun.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台行程列表 VO
 *
 * <p>在行程实体基础上，附带列表展示所需的「所属用户名 / 景点数」等统计字段。</p>
 */
@Data
public class AdminTripVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 行程ID */
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 所属用户名 */
    private String username;

    /** 所属用户头像 */
    private String avatar;

    /** 行程标题 */
    private String title;

    /** 行程天数 */
    private Integer days;

    /** 同行人 */
    private String companion;

    /** 兴趣偏好 */
    private String interests;

    /** 预算 */
    private BigDecimal budget;

    /** 状态：0-草稿，1-正常 */
    private Integer status;

    /** 封面图 */
    private String coverImage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 每日明细条数 */
    private Integer dayCount;

    /** 涉及景点数（去重） */
    private Integer scenicCount;
}
