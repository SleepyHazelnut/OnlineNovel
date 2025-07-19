package com.inkisle.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 同一用户短时间内重复调用同一接口时，只放行第一次
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatSubmit {

    /** 锁的秒数，过了这段时间才可以再调 */
    long expire() default 5;

    /** 被挡住时返回的提示 */
    String message() default "操作处理中，请稍候";
}
