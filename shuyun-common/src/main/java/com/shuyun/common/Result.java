package com.shuyun.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应结果
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功状态码 */
    public static final Integer SUCCESS_CODE = 200;

    /** 失败状态码 */
    public static final Integer FAIL_CODE = 500;

    /** 状态码：200-成功，500-失败 */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 业务数据 */
    private T data;

    /**
     * 成功（无数据）
     *
     * @return 成功结果
     */
    public static <T> Result<T> ok() {
        return new Result<>(SUCCESS_CODE, "ok", null);
    }

    /**
     * 成功（带数据）
     *
     * @param data 业务数据
     * @return 成功结果
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, "ok", data);
    }

    /**
     * 成功（自定义提示信息 + 数据）
     *
     * @param msg  提示信息
     * @param data 业务数据
     * @return 成功结果
     */
    public static <T> Result<T> ok(String msg, T data) {
        return new Result<>(SUCCESS_CODE, msg, data);
    }

    /**
     * 失败（默认状态码 500）
     *
     * @param msg 提示信息
     * @return 失败结果
     */
    public static <T> Result<T> fail(String msg) {
        return new Result<>(FAIL_CODE, msg, null);
    }

    /**
     * 失败（自定义状态码 + 提示信息）
     *
     * @param code 状态码
     * @param msg  提示信息
     * @return 失败结果
     */
    public static <T> Result<T> fail(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }
}
