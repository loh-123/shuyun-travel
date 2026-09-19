package com.shuyun.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分页查询结果
 *
 * @param <T> 记录类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private Long total;

    /** 当前页数据 */
    private List<T> records;

    /** 当前页码 */
    private Integer page;

    /** 每页大小 */
    private Integer size;

    /**
     * 构建分页结果
     *
     * @param total   总记录数
     * @param records 当前页数据
     * @param page    当前页码
     * @param size    每页大小
     * @param <T>     记录类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(Long total, List<T> records, Integer page, Integer size) {
        return new PageResult<>(total, records, page, size);
    }
}
