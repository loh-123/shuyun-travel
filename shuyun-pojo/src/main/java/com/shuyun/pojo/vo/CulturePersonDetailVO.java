package com.shuyun.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 传承人详情 VO
 *
 * <p>{@link #brief} 为简介全文，供详情页正文展示。</p>
 */
@Data
public class CulturePersonDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 传承人ID */
    private Long id;

    /** 姓名 */
    private String name;

    /** 头像路径 */
    private String avatar;

    /** 简介（全文） */
    private String brief;

    /** 代表文化名称 */
    private String cultureName;

    /** 关联文化ID（可跳转 /culture/{id}） */
    private Long cultureId;

    /** 视频链接 */
    private String videoUrl;
}
