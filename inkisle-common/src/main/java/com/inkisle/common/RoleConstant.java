package com.inkisle.common;

/**
 * 三种角色。发 token 和拦截器校验都用这里的值，别处别再写字符串
 */
public final class RoleConstant {

    /** 读者 */
    public static final String USER = "user";

    /** 作者 */
    public static final String AUTHOR = "author";

    /** 管理员 */
    public static final String ADMIN = "admin";

    private RoleConstant() {
    }

}
