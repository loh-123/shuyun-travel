package com.shuyun.pojo.vo;

import com.shuyun.pojo.entity.UserTripDay;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 行程 VO（含每日明细）
 */
@Data
public class UserTripVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 行程ID */
    private Long id;

    /** 用户ID */
    private Long userId;

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

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 每日明细 */
    private List<UserTripDay> dayList;
}
