package com.shuyun.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 备份文件 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BackupFileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件名 */
    private String filename;

    /** 文件大小（字节） */
    private Long size;

    /** 创建时间（yyyy-MM-dd HH:mm:ss） */
    private String createTime;
}
