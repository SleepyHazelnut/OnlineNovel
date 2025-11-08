package com.inkisle.interceptor;

import com.inkisle.common.ResultCode;
import com.inkisle.common.RoleConstant;
import com.inkisle.common.TokenVersionStore;
import com.inkisle.common.UserStatusCache;
import com.inkisle.service.support.AccountStatusResolver;
import com.inkisle.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 校验 token、角色、身份版本，还有账号封没封
 *
 * 早先这里只校验签名和角色，理由是「登录那步已经拦了」，
 * 结果封禁只在书籍服务生效：被封的读者照样能签到、改资料、改密码，
 * 被删掉的管理员还能在 token 到期前接着封别人、过审作者。
 * 现在和书籍服务用同一份缓存判断，封禁时用户服务删 key，下一个请求就生效。
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
    private AccountStatusResolver accountStatusResolver;

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
        // 登出、改密之后签发的旧 token 一律不认，否则改完密码原会话还能用满 24 小时
        if (!tokenVersionStore.matches(role, userId, claims.get("ver", String.class))) {
            return reject(response, ResultCode.UNAUTHORIZED.getCode(), "登录状态已失效，请重新登录");
        }

        // 0 正常，1 封禁，null 是账号已注销。本服务回源直接查库，不再绕一圈 Feign
        Integer status = userStatusCache.get(role, userId, () -> accountStatusResolver.resolve(role, userId));
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
