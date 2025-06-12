package com.inkisle.interceptor;

import com.inkisle.common.ResultCode;
import com.inkisle.common.RoleConstant;
import com.inkisle.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 登录 + 角色拦截器
 * 统一校验 token 并判断角色，避免每个 Service 各自重复写权限判断
 * 角色按路径前缀划分：`/admin/**` 只放行管理员，`/author/**` 只放行作者，其余接口登录即可
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 浏览器跨域会先发 OPTIONS 预检请求，该请求不带 token，不放行会导致跨域请求被拦截
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith(BEARER_PREFIX)) {
            token = token.substring(BEARER_PREFIX.length());
        }

        Claims claims = jwtUtil.parse(token);
        if (claims == null) {
            return reject(response, ResultCode.UNAUTHORIZED);
        }

        String role = claims.get("role", String.class);
        if (!checkRole(request.getRequestURI(), role)) {
            return reject(response, ResultCode.FORBIDDEN);
        }

        request.setAttribute("userId", Long.valueOf(claims.getSubject()));
        request.setAttribute("role", role);
        return true;
    }

    /**
     * 按路径前缀判断这个接口该由哪种角色访问
     */
    private boolean checkRole(String uri, String role) {
        if (uri.startsWith("/admin/")) {
            return RoleConstant.ADMIN.equals(role);
        }
        if (uri.startsWith("/author/")) {
            return RoleConstant.AUTHOR.equals(role);
        }
        // 其余为需登录的读者接口，管理端后台也会调用，不作角色限制
        return true;
    }

    /**
     * 拦截器执行早于 Controller，全局异常处理器尚未生效，
     * 因此需在此手动构造与 `Result` 同结构的 JSON 写入响应
     */
    private boolean reject(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setStatus(resultCode.getCode());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + resultCode.getCode()
                + ",\"msg\":\"" + resultCode.getMsg() + "\",\"data\":null}");
        return false;
    }

}
