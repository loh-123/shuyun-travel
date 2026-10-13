package com.shuyun.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 备份可选数据表 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BackupTableVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 表名 */
    private String name;

    /** 记录数 */
    private Long rows;
}
