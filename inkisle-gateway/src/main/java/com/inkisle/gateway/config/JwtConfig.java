package com.inkisle.gateway.config;

import com.inkisle.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 单独声明 JwtUtil
 * 网关只扫 com.inkisle.gateway，扫不到 common 的组件，而且 common 里那套
 * 全局异常处理是 MVC 的，整包扫进来 WebFlux 起不来。就取用得到的这一个类
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil();
    }

}
