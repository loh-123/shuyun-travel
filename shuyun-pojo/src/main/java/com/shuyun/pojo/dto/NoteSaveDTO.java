package com.shuyun.pojo.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 笔记保存 DTO
 */
@Data
public class NoteSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 关联对象类型：SCENIC / CULTURE（可空） */
    private String targetType;

    /** 关联对象ID（可空） */
    private Long targetId;

    /** 关联对象名称（可自由填写） */
    private String targetName;

    /** 标签（逗号分隔） */
    private String tags;
}
