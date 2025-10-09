package com.inkisle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

// Feign 接口在 common 里，默认只扫当前模块，要指过去
@EnableFeignClients(basePackages = "com.inkisle.client")
// 阅读计数定期回写、榜单过期清理都靠定时任务
@EnableScheduling
@SpringBootApplication
public class BookServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookServiceApplication.class, args);
    }

}
