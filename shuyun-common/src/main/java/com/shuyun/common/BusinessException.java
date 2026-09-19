package com.shuyun.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * <p>业务校验不通过时抛出，由 {@link GlobalExceptionHandler} 统一捕获并转换为 {@link Result}。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误状态码 */
    private final Integer code;

    /**
     * 使用默认状态码 500 构造业务异常
     *
     * @param msg 异常信息
     */
    public BusinessException(String msg) {
        super(msg);
        this.code = Result.FAIL_CODE;
    }

    /**
     * 使用自定义状态码构造业务异常
     *
     * @param code 错误状态码
     * @param msg  异常信息
     */
    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
    }
}
