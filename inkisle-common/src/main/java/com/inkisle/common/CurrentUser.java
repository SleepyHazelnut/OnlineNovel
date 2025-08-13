package com.inkisle.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前登录用户，拦截器将 id 写入 request，controller 从中读取
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

}
