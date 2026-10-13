package com.shuyun.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 非遗列表项 VO
 */
@Data
public class HeritageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 非遗ID */
    private Long id;

    /** 名称 */
    private String name;

    /** 类别 */
    private String category;

    /** 级别 */
    private String level;

    /** 传承人 */
    private String inheritor;

    /** 简介 */
    private String summary;

    /** 封面图（取图片集中第一张，列表卡片用） */
    private String coverImage;
}
