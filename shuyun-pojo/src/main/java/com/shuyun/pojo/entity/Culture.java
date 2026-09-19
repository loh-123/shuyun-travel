package com.shuyun.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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

    /** 视频地址 */
    @TableField("video_url")
    private String videoUrl;

    /** 标签（逗号分隔） */
    @TableField("tags")
    private String tags;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 逻辑删除：0-未删除，1-已删除 */
    @TableField("deleted")
    private Integer deleted;
}
