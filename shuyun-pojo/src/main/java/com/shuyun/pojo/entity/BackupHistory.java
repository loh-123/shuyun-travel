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
 * 备份历史记录
 *
 * <p>每次创建备份时写入一条元数据，记录文件名、包含的表与文件大小。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("backup_history")
public class BackupHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文件名 */
    @TableField("filename")
    private String filename;

    /** 包含的表（逗号分隔） */
    @TableField("tables")
    private String tables;

    /** 文件大小（字节） */
    @TableField("size")
    private Long size;

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
