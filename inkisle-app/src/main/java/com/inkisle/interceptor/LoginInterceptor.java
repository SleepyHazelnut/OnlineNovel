package com.inkisle.interceptor;

import com.inkisle.common.ResultCode;
import com.inkisle.common.RoleConstant;
import com.inkisle.entity.AuthorInfo;
import com.inkisle.entity.UserInfo;
import com.inkisle.mapper.AdminInfoMapper;
import com.inkisle.mapper.AuthorInfoMapper;
import com.inkisle.mapper.UserInfoMapper;
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
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private AuthorInfoMapper authorInfoMapper;

    @Autowired
    private AdminInfoMapper adminInfoMapper;

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
        if (!enabled(role, userId)) {
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

    /** 管理员没有状态列，停用靠删记录，这里只要求记录还在 */
    private boolean enabled(String role, Long userId) {
        if (RoleConstant.USER.equals(role)) {
            UserInfo user = userInfoMapper.selectById(userId);
            return user != null && !disabled(user.getStatus());
        }
        if (RoleConstant.AUTHOR.equals(role)) {
            AuthorInfo author = authorInfoMapper.selectById(userId);
            return author != null && !disabled(author.getStatus());
        }
        if (RoleConstant.ADMIN.equals(role)) {
            return adminInfoMapper.selectById(userId) != null;
        }
        return false;
    }

    /** 0 正常，1 封禁（作者表写作「禁用」） */
    private boolean disabled(Integer status) {
        return status != null && status == 1;
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
