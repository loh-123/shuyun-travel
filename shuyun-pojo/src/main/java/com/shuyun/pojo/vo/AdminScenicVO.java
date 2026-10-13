package com.shuyun.pojo.vo;

import com.shuyun.pojo.entity.ScenicSpot;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 后台景点列表 / 详情 VO
 *
 * <p>在景点实体基础上，附带列表与编辑抽屉需要的展示字段：</p>
 * <ul>
 *     <li>{@link #cityName} 所属市州名称</li>
 *     <li>{@link #collectCount} 收藏数</li>
 *     <li>{@link #images} 详情图地址列表（编辑抽屉回显）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminScenicVO extends ScenicSpot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属市州名称 */
    private String cityName;

    /** 收藏数 */
    private Integer collectCount;

    /** 详情图地址列表 */
    private List<String> images;
}
