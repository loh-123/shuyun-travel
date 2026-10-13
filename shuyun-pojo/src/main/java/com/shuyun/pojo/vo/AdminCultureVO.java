package com.shuyun.pojo.vo;

import com.shuyun.pojo.entity.Culture;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 后台文化列表 / 详情 VO
 *
 * <p>在文化实体基础上，附带列表展示需要的统计字段 {@link #relatedScenicCount}（相关景点数）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminCultureVO extends Culture implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 相关景点数 */
    private Integer relatedScenicCount;
}
