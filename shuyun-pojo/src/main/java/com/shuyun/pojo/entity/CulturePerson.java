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
 * 川蜀文化传承人
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("culture_person")
public class CulturePerson implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 姓名 */
    @TableField("name")
    private String name;

    /** 简介 */
    @TableField("brief")
    private String brief;

    /** 代表文化（Excel 原字段） */
    @TableField("culture_name")
    private String cultureName;

    /** 关联到 culture 表的 id（导入时自动匹配） */
    @TableField("culture_id")
    private Long cultureId;

    /** 视频链接 */
    @TableField("video_url")
    private String videoUrl;

    /** 头像路径，如 /images/person/pengdenghuai.jpg */
    @TableField("avatar")
    private String avatar;

    /** 排序 */
    @TableField("sort_order")
    private Integer sortOrder;

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
