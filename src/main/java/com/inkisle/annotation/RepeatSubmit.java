package com.inkisle.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交注解
 * 拦截同一用户短时间内对同一接口发起的重复请求（如前端按钮未及时禁用导致的连点）
 * 具体逻辑由 `RepeatSubmitAspect` 切面实现，注解本身只提供两个参数：
 * 锁的秒数与被拦截时的提示信息
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatSubmit {

    /**
     * 锁保留秒数，同时表示多少秒内视为重复请求，默认 5 秒
     */
    long expire() default 5;

    /**
     * 被拦截时返回给前端的提示信息
     */
    String message() default "操作正在处理，别连着点";

}
