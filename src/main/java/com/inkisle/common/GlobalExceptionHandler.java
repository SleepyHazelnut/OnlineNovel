package com.inkisle.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理，Controller 不用再一个个 try-catch
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常，提示信息可以直接给用户看
     */
    @ExceptionHandler(BusinessException.class)
    public Result handleBusiness(BusinessException e) {
        return Result.fail(e.getMessage());
    }

    /**
     * 兜底：其他异常一律记日志，返回统一的模糊提示
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统繁忙，请稍后再试");
    }

}
