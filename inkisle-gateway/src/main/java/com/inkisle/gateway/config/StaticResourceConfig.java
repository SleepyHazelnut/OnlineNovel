package com.inkisle.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.resource.PathResourceResolver;
import reactor.core.publisher.Mono;

/**
 * 上传目录的静态映射，/upload 指到磁盘那个目录，别的服务不用再各暴露一份
 */
@Configuration
public class StaticResourceConfig implements WebFluxConfigurer {

    @Value("${file.upload-path}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:" + uploadPath + "/");

        // 读者端是 Vue 单页应用，构建产物放在 /app 下。路径交给前端路由后，
        // 刷新 /app/books/1 这类地址磁盘上没有对应文件，得回退到 index.html 再由 vue-router 接管。
        // 带扩展名的请求仍按原样查找，避免把缺失的 js、css 也回成一张 html
        registry.addResourceHandler("/app/**")
                .addResourceLocations("classpath:/static/app/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Mono<Resource> getResource(String resourcePath, Resource location) {
                        return super.getResource(resourcePath, location)
                                .switchIfEmpty(Mono.defer(() -> resourcePath.contains(".")
                                        ? Mono.empty()
                                        : super.getResource("index.html", location)));
                    }
                });
    }

}
