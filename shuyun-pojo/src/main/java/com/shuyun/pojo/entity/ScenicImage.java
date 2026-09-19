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
 * 景点图片
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("scenic_image")
public class ScenicImage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 景点ID */
    @TableField("scenic_id")
    private Long scenicId;

    /** 图片地址 */
    @TableField("image_url")
    private String imageUrl;

    /** 排序 */
    @TableField("sort_order")
    private Integer sortOrder;

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
