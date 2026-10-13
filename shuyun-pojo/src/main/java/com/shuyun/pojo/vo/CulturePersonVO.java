package com.shuyun.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 传承人列表项 VO
 *
 * <p>用于列表 / 相关传承人卡片，{@link #brief} 已在服务层截断，避免前端渲染过长。</p>
 */
@Data
public class CulturePersonVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 传承人ID */
    private Long id;

    /** 姓名 */
    private String name;

    /** 头像路径 */
    private String avatar;

    /** 代表文化名称 */
    private String cultureName;

    /** 简介（截断到 60 字） */
    private String brief;
}
