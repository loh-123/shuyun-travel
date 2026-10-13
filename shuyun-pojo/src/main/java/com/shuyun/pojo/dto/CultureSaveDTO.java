package com.shuyun.pojo.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 文化新增 / 编辑入参
 *
 * <p>供后台「文化管理」抽屉表单提交使用，字段与 {@code culture} 表一一对应。</p>
 */
@Data
public class CultureSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（新增时为空） */
    private Long id;

    /** 名称 */
    private String name;

    /** 分类 */
    private String category;

    /** 封面图 */
    private String coverImage;

    /** 摘要（100 字以内） */
    private String summary;

    /** 正文（富文本 HTML） */
    private String content;

    /** 关联景点ID（逗号分隔） */
    private String relatedScenicIds;

    /** 传承人原文（每行一条：姓名|身份|一句话） */
    private String inheritorText;

    /** 视频地址 */
    private String videoUrl;

    /** 标签（逗号分隔） */
    private String tags;
}
