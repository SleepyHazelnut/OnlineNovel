package com.inkisle.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记服务间内部接口
 *
 * 两件事跟它绑定：
 * 1. 请求要带内部令牌（见 InternalAuthFilter），网关不转发这个前缀，直连端口没有令牌也进不来
 * 2. 出错要返回非 2xx（见 InternalExceptionHandler）。Feign 默认只看 HTTP 状态码，
 *    失败也回 200 的话，返回 void 的接口会被调用方当成成功 —— 章节审核曾经就这么「通过」过
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InternalApi {
}
