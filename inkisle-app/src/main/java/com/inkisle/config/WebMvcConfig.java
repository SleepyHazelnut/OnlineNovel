package com.inkisle.config;

import com.inkisle.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录拦截器、将上传目录映射为静态资源
 * 通过实现 `WebMvcConfigurer` 覆写对应方法完成，比只在 `application.yml` 中配置更灵活
 */
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
                        "/user/login", "/user/register",
                        // 作者端注册登录
                        "/author/register", "/author/login",
                        // 管理端登录
                        "/admin/login",
                        // 图形验证码
                        "/captcha/**",
                        // 书籍、分类、章节、榜单对外只读，游客也能看
                        "/book/**", "/category/**", "/chapter/**", "/rank/**",
                        // 首页推荐位同样是公开内容
                        "/home/**",
                        // 评论列表可匿名浏览，只有发言要登录
                        "/comment/list",
                        // 上传后的图片要能直接被浏览器打开
                        "/upload/**",
                        // 静态页面与前端资源，css / js 若不放行，未登录时样式无法加载
                        "/", "/*.html", "/css/**", "/js/**", "/static/**", "/favicon.ico", "/error"
                );
    }

    /**
     * 上传的图片存于服务器磁盘，无法直接通过 URL 访问，
     * 此处将该目录映射为 `/upload/**` 的静态资源路径
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }

}
