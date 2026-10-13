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
 * 非物质文化遗产
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("heritage")
public class Heritage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名称 */
    @TableField("name")
    private String name;

    /** 简介网址 */
    @TableField("website")
    private String website;

    /** 类别 */
    @TableField("category")
    private String category;

    /** 级别 */
    @TableField("level")
    private String level;

    /** 传承人 */
    @TableField("inheritor")
    private String inheritor;

    /** 简介 */
    @TableField("summary")
    private String summary;

    /** 视频地址 */
    @TableField("video_url")
    private String videoUrl;

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
