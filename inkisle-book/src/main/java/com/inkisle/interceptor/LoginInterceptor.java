package com.inkisle.interceptor;

import com.inkisle.client.UserClient;
import com.inkisle.common.ResultCode;
import com.inkisle.common.RoleConstant;
import com.inkisle.common.TokenVersionStore;
import com.inkisle.common.UserStatusCache;
import com.inkisle.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 校验 token、角色，还有账号封没封
 * 封禁不能只看 token，token 有效期 24 小时，签发之后账号可能被封了，
 * 只拦登录的话要等一天才生效。账号表在用户服务，这里问一次，
 * 结果缓存 30 秒，封禁时用户服务会主动删掉那份缓存
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserStatusCache userStatusCache;

    @Autowired
    private TokenVersionStore tokenVersionStore;

    @Autowired
    private UserClient userClient;

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
        if (!checkRole(request.getRequestURI(), role)) {
            return reject(response, ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMsg());
        }

        Long userId = Long.valueOf(claims.getSubject());
        // token 是登出/改密之前签的，服务端不认它，否则改完密码旧会话还能用满 24 小时
        if (!tokenVersionStore.matches(role, userId, claims.get("ver", String.class))) {
            return reject(response, ResultCode.UNAUTHORIZED.getCode(), "登录状态已失效，请重新登录");
        }
        // 0 正常，1 封禁，null 是账号已注销。账号表在用户服务，回源走 Feign
        Integer status = userStatusCache.get(role, userId, () -> userClient.status(role, userId));
        if (status == null || status == 1) {
            return reject(response, ResultCode.FORBIDDEN.getCode(), "账号已被封禁或不存在");
        }

        request.setAttribute("userId", userId);
        request.setAttribute("role", role);
        return true;
    }

    private boolean checkRole(String uri, String role) {
        if (uri.startsWith("/admin/")) {
            return RoleConstant.ADMIN.equals(role);
        }
        // 写书、发章节走作者端，读者不能进来
        if (uri.startsWith("/author/")) {
            return RoleConstant.AUTHOR.equals(role);
        }
        return RoleConstant.USER.equals(role);
    }

    /** 拦截器早于全局异常处理器，这里只能自己拼 JSON */
    private boolean reject(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code
                + ",\"msg\":\"" + msg + "\",\"data\":null}");
        return false;
    }

}
