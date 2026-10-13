package com.shuyun.pojo.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 文化详情 VO
 *
 * <p>在 {@link CultureVO} 基础上补充详情页字段：简介网址、视频地址、相关景点 / 相关人物
 * （采集文本）、图片集 {@link #images}（URL 列表，后端不拼接）。</p>
 *
 * <p>同时保留两项「已解析好的结构化数据」供详情页直接渲染：
 * {@link #relatedScenics}（由 related_scenic_ids 解析）与 {@link #inheritors}（由 inheritors 文本解析）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CultureDetailVO extends CultureVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 简介网址 */
    private String website;

    /** 视频地址 */
    private String videoUrl;

    /** 相关景点（逗号分隔的名称，来自采集数据） */
    private String relatedScenic;

    /** 相关人物（逗号分隔，来自采集数据） */
    private String relatedPerson;

    /** 图片集地址列表（按 sort_order 升序） */
    private List<String> images;

    /** 关联景点（结构化，最多 3 条） */
    private List<ScenicSpotVO> relatedScenics;

    /** 传承人：[{name, title, intro}] */
    private List<Map<String, String>> inheritors;
}
