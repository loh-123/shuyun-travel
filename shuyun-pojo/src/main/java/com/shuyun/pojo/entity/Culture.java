package com.shuyun.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文化
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("culture")
public class Culture implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 名称 */
    @TableField("name")
    private String name;

    /** 分类 */
    @TableField("category")
    private String category;

    /** 封面图 */
    @TableField("cover_image")
    private String coverImage;

    /** 摘要 */
    @TableField("summary")
    private String summary;

    /** 正文 */
    @TableField("content")
    private String content;

    /** 简介网址（百度百科等） */
    @TableField("website")
    private String website;

    /** 关联景点（逗号分隔的景点名称，来自 Excel 采集） */
    @TableField("related_scenic")
    private String relatedScenic;

    /** 相关人物（逗号分隔，来自 Excel 采集） */
    @TableField("related_person")
    private String relatedPerson;

    /** 关联景点ID（逗号分隔，后台维护） */
    @TableField("related_scenic_ids")
    private String relatedScenicIds;

    /** 传承人原文（每行一条：姓名|身份|一句话；详情接口会解析为结构化的 inheritors） */
    @TableField("inheritors")
    private String inheritorText;

    /** 视频地址 */
    @TableField("video_url")
    private String videoUrl;

    /** 标签（逗号分隔） */
    @TableField("tags")
    private String tags;

    /** 浏览次数（人气） */
    @TableField("view_count")
    private Integer viewCount;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 逻辑删除：0-未删除，1-已删除 */
    @TableField("deleted")
    private Integer deleted;
}
