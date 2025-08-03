package com.inkisle.interceptor;

import com.inkisle.client.UserClient;
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
 * 统一校验 token 与角色：/admin/** 只放管理员，/author/** 只放作者，其余按读者处理
 * 账号状态问用户服务要：这里只剩创作助手与统计，为它留三张账号表的 mapper 不值得
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserClient userClient;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 跨域预检不带 token
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
        if (!checkRole(request.getRequestURI(), role)) {
            return reject(response, ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMsg());
        }

        Long userId = Long.valueOf(claims.getSubject());
        // token 有 24 小时有效期，签发之后可能已被封禁，只拦登录入口不够
        // 这里不缓存，缓存会让封禁延迟生效
        Integer status = userClient.status(role, userId);
        if (status == null || status == 1) {
            return reject(response, ResultCode.FORBIDDEN.getCode(), "账号已被封禁或不存在");
        }

        request.setAttribute("userId", userId);
        request.setAttribute("role", role);
        return true;
    }

    /**
     * 读者端接口必须要求读者：三张账号表各自自增，作者 id=1 和读者 id=1 是两个人，
     * 按「登录即可」放行的话作者能拿自己的 token 操作读者的数据
     */
    private boolean checkRole(String uri, String role) {
        if (uri.startsWith("/admin/")) {
            return RoleConstant.ADMIN.equals(role);
        }
        if (uri.startsWith("/author/")) {
            return RoleConstant.AUTHOR.equals(role);
        }
        // 换头像用读者身份，作者传封面用作者身份，两边都走 /file/upload
        if (uri.startsWith("/file/")) {
            return RoleConstant.USER.equals(role) || RoleConstant.AUTHOR.equals(role);
        }
        return RoleConstant.USER.equals(role);
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
