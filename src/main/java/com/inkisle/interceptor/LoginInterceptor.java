package com.inkisle.interceptor;

import com.inkisle.common.ResultCode;
import com.inkisle.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：拦下需要登录的接口，把 userId 塞进 request 给 Controller 用
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 浏览器的跨域预检不带 token，直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith(BEARER_PREFIX)) {
            token = token.substring(BEARER_PREFIX.length());
        }

        Long userId = jwtUtil.getUserId(token);
        if (userId == null) {
            // 这里没走到 Controller，@RestControllerAdvice 兜不住，只能手写返回体
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + ResultCode.UNAUTHORIZED.getCode()
                    + ",\"msg\":\"" + ResultCode.UNAUTHORIZED.getMsg() + "\",\"data\":null}");
            return false;
        }

        request.setAttribute("userId", userId);
        return true;
    }

}
