package com.inkisle.config;

import com.inkisle.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 注册登录本身不能拦
                        "/user/login", "/user/register",
                        "/author/register", "/author/login",
                        "/admin/login",
                        // 图形验证码
                        "/captcha/**",
                        // 积分规则是公开的：不登录也该知道怎么攒分
                        "/point/rules",
                        // 内部接口由服务间调用，不带用户 token
                        "/internal/**"
                );
    }

}
