package com.inkisle.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理
 * 通过 `@RestControllerAdvice` 集中处理异常，避免每个 Controller 各自 try-catch 并拼装 `Result`
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常，提示信息为预置文案，可直接返回给用户
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.fail(e.getMessage());
    }

    /**
     * `@Valid` 校验失败，只取第一条提示返回
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse(ResultCode.PARAM_ERROR.getMsg());
        return Result.fail(ResultCode.PARAM_ERROR, msg);
    }

    /**
     * 静态资源找不到等 404 场景。若不单独处理会落入下方兜底分支，被统一返回「操作失败」
     * Spring Boot 3.2 起该场景抛出 `NoResourceFoundException`，故单独捕获
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.fail(ResultCode.NOT_FOUND);
    }

    /**
     * 兜底：未被上述分支捕获的异常均落到这里
     * 记录日志便于排查，返回给前端的是模糊的「操作失败」，避免堆栈信息外泄
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.FAIL.getMsg());
    }

}
