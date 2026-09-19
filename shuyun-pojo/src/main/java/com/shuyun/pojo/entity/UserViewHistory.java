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
 * 浏览足迹
 *
 * <p>同一用户对同一对象只保留一条记录，重复浏览时刷新 {@code viewTime}。
 * 名称 / 封面 / 副标题做了冗余存储，列表页无需再联表查询。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_view_history")
public class UserViewHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 对象类型：SCENIC / CULTURE */
    @TableField("target_type")
    private String targetType;

    /** 对象ID */
    @TableField("target_id")
    private Long targetId;

    /** 对象名称（冗余） */
    @TableField("target_name")
    private String targetName;

    /** 封面图（冗余） */
    @TableField("cover_image")
    private String coverImage;

    /** 副标题：市州 / 分类（冗余） */
    @TableField("sub_title")
    private String subTitle;

    /** 最近浏览时间 */
    @TableField("view_time")
    private LocalDateTime viewTime;

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
