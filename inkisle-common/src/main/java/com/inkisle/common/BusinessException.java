package com.inkisle.common;

/**
 * 业务异常
 * Service 层的业务问题（如书籍已被他人买走）直接抛出该异常，无需在 Controller 中逐层 try-catch，
 * 最终由 `GlobalExceptionHandler` 统一捕获并转换为 `Result`
 * 不传状态码时默认为 500，需要区分时可传入自定义码
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
