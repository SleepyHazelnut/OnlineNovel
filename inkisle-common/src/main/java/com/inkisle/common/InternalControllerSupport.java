package com.inkisle.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 内部接口控制器的基类，给服务间调用换一套错误约定
 *
 * 对外接口一直用 200 + code 表达业务结果，前端也按这个读，改起来牵连面大；
 * 但服务之间不能这样：Feign 不解码响应体，返回 void 的接口失败时，
 * 声明的返回类型根本没有机会看到错误码，调用方只会认为「成功了」——
 * 章节审核曾经就这么被当成通过，日志还打了「审核通过」。
 *
 * 这里用继承而不是 @RestControllerAdvice：控制器自带的 @ExceptionHandler
 * 一定先于全局 advice 生效，不依赖 advice 之间的排序
 */
@InternalApi
public abstract class InternalControllerSupport {

    private static final Logger log = LoggerFactory.getLogger(InternalControllerSupport.class);

    /**
     * 业务校验失败按 400 回：调用方据此抛出的是可读的业务提示，而不是「服务故障」。
     * 顺带把 400 和 500 分开，调用方重试前至少能判断这是不是能靠重试解决的问题
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ResultCode.PARAM_ERROR, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("内部接口异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.FAIL.getMsg()));
    }

}
