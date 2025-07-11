package com.inkisle.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前登录用户。拦截器把 id 写进 request，控制器从这里取
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

}
