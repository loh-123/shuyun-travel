package com.shuyun.pojo.dto;

import com.shuyun.pojo.entity.UserTripDay;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 行程保存请求
 */
@Data
public class TripSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

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

    /** 封面图 */
    private String coverImage;

    /** 状态：0-草稿，1-正常 */
    private Integer status;

    /** 每日明细 */
    private List<UserTripDay> dayList;
}
