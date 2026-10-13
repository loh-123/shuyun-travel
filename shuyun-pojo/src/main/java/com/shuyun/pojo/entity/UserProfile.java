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
 * 用户画像
 *
 * <p>供 AI 助手注入个性化上下文（兴趣偏好 / 同行类型 / 预算范围等）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_profile")
public class UserProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 年龄段（如 26-35） */
    @TableField("age_range")
    private String ageRange;

    /** 性别：男 / 女 / 其他 */
    @TableField("gender")
    private String gender;

    /** 常驻地域（市州名） */
    @TableField("region")
    private String region;

    /** 兴趣偏好（逗号分隔） */
    @TableField("interests")
    private String interests;

    /** 同行类型（独自/情侣/亲子/父母/朋友） */
    @TableField("companion")
    private String companion;

    /** 预算范围（文本，如 3000-5000 元） */
    @TableField("budget")
    private String budget;

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
