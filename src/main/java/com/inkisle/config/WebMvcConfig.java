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
                        "/user/login", "/user/register", "/user/list",
                        // 图形验证码
                        "/captcha/**",
                        // 书籍、分类、章节对外只读，游客也能看
                        "/book/**", "/category/**", "/chapter/**",
                        // 评论列表可匿名浏览，只有发言要登录
                        "/comment/list",
                        // 静态页面
                        "/", "/*.html", "/css/**", "/js/**", "/static/**", "/favicon.ico", "/error"
                );
    }

}
