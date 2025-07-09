package com.inkisle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * 启动类
 * 在此开启缓存开关 `@EnableCaching`，未开启时 `@Cacheable` 不生效
 */
@SpringBootApplication
@EnableCaching
public class InkisleApplication {

    public static void main(String[] args) {
        SpringApplication.run(InkisleApplication.class, args);
    }

}
