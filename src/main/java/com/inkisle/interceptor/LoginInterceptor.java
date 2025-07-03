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
 * 登录 + 角色拦截器
 * 统一校验 token 并判断角色，避免每个 Service 各自重复写权限判断
 * 角色按路径前缀划分：`/admin/**` 只放行管理员，`/author/**` 只放行作者，
 * 其余需登录的接口一律要求读者
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
            return reject(response, ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg());
        }

        String role = claims.get("role", String.class);
        if (!checkRole(request.getRequestURI(), role)) {
            return reject(response, ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMsg());
        }

        Long userId = Long.valueOf(claims.getSubject());
        // token 有效期 24 小时，签发之后账号状态可能已变。只拦登录入口不够：
        // 封禁或删除后，手里那个旧 token 仍能一直用到过期，故每次请求都回查一次
        // 三张表各按主键查一次，代价是一次索引等值查询；不加缓存是刻意的——
        // 缓存会让封禁延迟生效，而封禁要立刻生效正是这里要解决的问题
        if (!enabled(role, userId)) {
            return reject(response, ResultCode.FORBIDDEN.getCode(), "账号已被封禁或不存在");
        }

        request.setAttribute("userId", userId);
        request.setAttribute("role", role);
        return true;
    }

    /**
     * 按路径前缀判断这个接口该由哪种角色访问
     * 非 admin / author 前缀的接口都是读者端功能（书架、阅读进度、评论、签到、个人中心），
     * 统一要求读者角色：三张账号表各自自增，作者 id=1 与读者 id=1 指向的是两个人，
     * 若按「登录即可」放行，作者 token 调评论接口就会记到读者 1 名下
     */
    private boolean checkRole(String uri, String role) {
        if (uri.startsWith("/admin/")) {
            return RoleConstant.ADMIN.equals(role);
        }
        if (uri.startsWith("/author/")) {
            return RoleConstant.AUTHOR.equals(role);
        }
        // 图片上传是读者与作者共用的：个人中心换头像用读者身份，作者建书传封面用作者身份
        // 路径不在 /author/ 下，若按「其余一律读者」处理，作者传封面会被判成越权
        if (uri.startsWith("/file/")) {
            return RoleConstant.USER.equals(role) || RoleConstant.AUTHOR.equals(role);
        }
        return RoleConstant.USER.equals(role);
    }

    /**
     * 账号当前是否可用
     * admin_info 没有状态列，管理员停用靠删除记录实现，这里只要求记录仍存在
     */
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
        // 未知角色一律不放行
        return false;
    }

    /** 状态列：0 正常，1 封禁（作者表注释写作「禁用」，含义相同） */
    private boolean disabled(Integer status) {
        return status != null && status == 1;
    }

    /**
     * 拦截器执行早于 Controller，全局异常处理器尚未生效，
     * 因此需在此手动构造与 `Result` 同结构的 JSON 写入响应
     */
    private boolean reject(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code
                + ",\"msg\":\"" + msg + "\",\"data\":null}");
        return false;
    }

}
