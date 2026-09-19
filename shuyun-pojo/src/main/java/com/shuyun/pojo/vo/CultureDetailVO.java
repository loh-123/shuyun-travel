package com.shuyun.pojo.vo;

import com.shuyun.pojo.entity.Culture;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 文化详情 VO
 *
 * <p>在文化实体基础上，附带两项「已解析好的结构化数据」：</p>
 * <ul>
 *     <li>{@link #relatedScenics} 关联景点（由 related_scenic_ids 解析而来）</li>
 *     <li>{@link #inheritors} 传承人（由 inheritors 文本解析而来）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CultureDetailVO extends Culture implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联景点（最多 3 条） */
    private List<ScenicSpotVO> relatedScenics;

    /** 传承人：[{name, title, intro}] */
    private List<Map<String, String>> inheritors;
}
