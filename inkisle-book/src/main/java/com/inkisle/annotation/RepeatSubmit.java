package com.inkisle.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 同一主体短时间内重复调用同一接口时，只放行第一次
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatSubmit {

    /** 去重令牌的有效期，过了这段时间才可以再调 */
    long expire() default 5;

    /** 被挡住时返回的提示 */
    String message() default "操作处理中，请稍候";

    /**
     * 业务维度，SpEL 表达式，取方法参数里的资源 id。
     * 例如评论填 {@code #p0.bookId}、章节保存填 {@code #p0['bookId']}。
     * 不填就只按「主体 + 接口」加锁：同一用户在锁的有效期内
     * 评完 A 书再去评 B 书也会被拦，等于把不同资源当成同一个操作
     */
    String key() default "";

}
