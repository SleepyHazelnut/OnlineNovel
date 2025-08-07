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
 * 校验 token 和角色。本服务只有 /admin/stat 下两个接口，仅放行管理员
 * 未校验封禁状态，统计不写数据，被封账号最多多读一份报表，不值得一次远程调用
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith(BEARER_PREFIX)) {
            token = token.substring(BEARER_PREFIX.length());
        }

        Claims claims = jwtUtil.parse(token);
        if (claims == null) {
            return reject(response, ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg());
        }

        String role = claims.get("role", String.class);
        if (!RoleConstant.ADMIN.equals(role)) {
            return reject(response, ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMsg());
        }

        request.setAttribute("userId", Long.valueOf(claims.getSubject()));
        request.setAttribute("role", role);
        return true;
    }

    /** 拦截器早于全局异常处理器执行，这里只能自己拼 JSON */
    private boolean reject(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code
                + ",\"msg\":\"" + msg + "\",\"data\":null}");
        return false;
    }

}
