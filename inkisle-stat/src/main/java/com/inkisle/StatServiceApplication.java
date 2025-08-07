package com.inkisle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 统计服务。只读聚合，不写任何业务表，也不调别的服务
 */
@SpringBootApplication
public class StatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatServiceApplication.class, args);
    }

}
