package com.inkisle.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理，统一返回 Result
 *
 * 参数类错误给 400、唯一键冲突给 409，业务异常仍以 code 表达。
 * 这几种原来一律掉进兜底分支变成「操作失败」：
 * 用户少传一个参数、id 传成非数字、并发注册撞了唯一键，看到的都是同一句「操作失败」，无从下手
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.ok(Result.fail(ResultCode.FAIL, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse(ResultCode.PARAM_ERROR.getMsg());
        return badRequest(msg);
    }

    /** 参数类型对不上，比如路径上的 id 传了非数字 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return badRequest("参数 " + e.getName() + " 格式不对");
    }

    /** 请求体不是合法 JSON，或者字段类型与 DTO 不符 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return badRequest("请求体格式不对");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return badRequest("缺少参数 " + e.getParameterName());
    }

    /**
     * 唯一键冲突。注册、收藏这类接口本就该靠唯一索引保证幂等，
     * 撞上说明有人抢先一步，给 409 比「操作失败」有用得多
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突", e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.fail(ResultCode.CONFLICT));
    }

    /** 静态资源 404 不单独处理会进入兜底分支，变成「操作失败」 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(ResultCode.NOT_FOUND));
    }

    /** 兜底。日志留堆栈，返回给前端的只有一句模糊提示 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.ok(Result.fail(ResultCode.FAIL.getMsg()));
    }

    private ResponseEntity<Result<Void>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(Result.fail(ResultCode.PARAM_ERROR, msg));
    }

}
