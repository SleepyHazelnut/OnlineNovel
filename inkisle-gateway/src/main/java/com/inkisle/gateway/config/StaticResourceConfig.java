package com.inkisle.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * 上传目录的静态映射
 * <p>
 * 图片落在服务器磁盘上，无法直接按 URL 取到；网关统一把 /upload/** 指到该目录，
 * 后端服务不必再各自暴露一份
 */
@Configuration
public class StaticResourceConfig implements WebFluxConfigurer {

    @Value("${file.upload-path}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }

}
