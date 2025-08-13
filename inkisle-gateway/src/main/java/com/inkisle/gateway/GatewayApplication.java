package com.inkisle.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关入口，静态页和上传都在这里对外，后端服务不当面给浏览器
 * 不能顺带扫描 com.inkisle.common，该包有 MVC 全局异常处理，
 * 网关是 WebFlux，扫进来缺 servlet 的类型，起不来
 */
@EnableDiscoveryClient
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

}
