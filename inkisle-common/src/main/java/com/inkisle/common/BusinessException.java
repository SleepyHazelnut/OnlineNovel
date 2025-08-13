package com.inkisle.common;

/**
 * 业务异常，service 里校验不过直接扔这个，全局异常处理会接住转成 Result
 */
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String msg) {
        this(500, msg);
    }

    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

}
