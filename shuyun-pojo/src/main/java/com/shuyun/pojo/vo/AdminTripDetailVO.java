package com.shuyun.pojo.vo;

import com.shuyun.pojo.entity.UserTripDay;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 后台行程详情 VO
 *
 * <p>在 {@link AdminTripVO} 基础上附加每日明细，供详情抽屉时间轴展示。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminTripDetailVO extends AdminTripVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 每日明细（按天正序） */
    private List<UserTripDay> dayList;
}
