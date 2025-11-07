package com.inkisle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkisle.common.BusinessException;
import com.inkisle.common.Result;
import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 服务间调用的公共约定：带上内部令牌，把非 2xx 转成业务异常
 *
 * 没有 ErrorDecoder 的时候，FeignException 会一路冒到全局兜底，
 * 前端只能看到「操作失败」；而返回 void 的内部接口更糟 ——
 * 只有 HTTP 状态码非 2xx 才会抛，所以内部控制器那边也统一改成了非 2xx。
 *
 * 按类名判断而不是直接引类型：统计服务没有引入 Feign，
 * 但这个配置和它一起被扫描到，直接引类型会让它启动失败
 */
@ConditionalOnClass(name = {"feign.RequestInterceptor", "feign.codec.ErrorDecoder"})
@Configuration
public class FeignSupportConfig {

    private static final Logger log = LoggerFactory.getLogger(FeignSupportConfig.class);

    private static final int MAX_BODY = 2048;

    /** 每个请求都带上，令牌来自同一个环境变量，不需要在调用点操心 */
    @Bean
    public RequestInterceptor internalTokenInterceptor(@Value("${inkisle.internal.token:}") String token) {
        return template -> {
            if (token != null && !token.isBlank()) {
                template.header(InternalAuthFilter.HEADER, token);
            }
        };
    }

    @Bean
    public ErrorDecoder resultErrorDecoder(ObjectMapper objectMapper) {
        return (methodKey, response) -> {
            String body = readBody(response);
            if (body != null) {
                try {
                    Result<?> result = objectMapper.readValue(body, Result.class);
                    if (result.getMsg() != null && !result.getMsg().isBlank()) {
                        return new BusinessException(
                                result.getCode() == null ? 500 : result.getCode(), result.getMsg());
                    }
                } catch (Exception e) {
                    // 不是标准 Result 结构（比如网关的 502 页面），落到下面按状态码处理
                    log.debug("解析下游错误响应失败 method={}", methodKey, e);
                }
            }
            return new BusinessException("调用 " + methodKey + " 失败，状态码 " + response.status());
        };
    }

    private String readBody(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream in = response.body().asInputStream()) {
            byte[] bytes = in.readNBytes(MAX_BODY);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

}
