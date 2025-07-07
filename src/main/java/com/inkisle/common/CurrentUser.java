package com.inkisle.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前登录用户。拦截器解析 token 后把 id 写进 request，
 * 控制器取用时统一走这里，省得每个方法都强转一次、再把属性名写错
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

}
