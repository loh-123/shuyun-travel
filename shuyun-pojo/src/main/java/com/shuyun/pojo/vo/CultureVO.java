package com.shuyun.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 文化列表项 VO
 */
@Data
public class CultureVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文化ID */
    private Long id;

    /** 名称 */
    private String name;

    /** 分类 */
    private String category;

    /** 封面图 */
    private String coverImage;

    /** 摘要 */
    private String summary;

    /** 正文（列表页卡片简介需要） */
    private String content;

    /** 标签（逗号分隔） */
    private String tags;
}
