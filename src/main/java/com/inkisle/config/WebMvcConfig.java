package com.inkisle.config;

import com.inkisle.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Value("${file.upload-path}")
    private String uploadPath;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 注册登录本身不能拦
                        "/user/login", "/user/register", "/user/list",
                        // 作者端注册登录
                        "/author/register", "/author/login",
                        // 管理端登录
                        "/admin/login",
                        // 图形验证码
                        "/captcha/**",
                        // 书籍、分类、章节、榜单对外只读，游客也能看
                        "/book/**", "/category/**", "/chapter/**", "/rank/**",
                        // 评论列表可匿名浏览，只有发言要登录
                        "/comment/list",
                        // 上传后的图片要能直接被浏览器打开
                        "/upload/**",
                        // 静态页面
                        "/", "/*.html", "/css/**", "/js/**", "/static/**", "/favicon.ico", "/error"
                );
    }

    /**
     * 把磁盘上的上传目录映射成 /upload/** 的静态资源
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }

}
