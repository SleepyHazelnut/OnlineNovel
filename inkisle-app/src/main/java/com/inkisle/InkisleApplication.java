package com.inkisle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 启动类
 * 缓存跟着书籍服务走了，这里不再开 `@EnableCaching`：
 * 没有缓存管理器时开着它，用到 `@Cacheable` 的地方会直接报错
 */
@SpringBootApplication
@EnableFeignClients
public class InkisleApplication {

    public static void main(String[] args) {
        SpringApplication.run(InkisleApplication.class, args);
    }

}
