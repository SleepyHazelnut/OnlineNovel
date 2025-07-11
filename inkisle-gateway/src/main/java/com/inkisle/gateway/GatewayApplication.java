package com.inkisle.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关入口
 * <p>
 * 静态页与上传目录都由这里对外提供，后端服务不再直接暴露给浏览器
 * 注意不必扫描 com.inkisle.common：那里有基于 Spring MVC 的全局异常处理，
 * 网关是 WebFlux，扫进来会因缺少 servlet 类型而起不来
 */
@EnableDiscoveryClient
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

}
