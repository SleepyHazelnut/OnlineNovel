package com.inkisle.common;

/**
 * 三种角色的标识
 * 签发 token 时写入 claim、拦截器校验权限均使用此处的值，
 * 不应在其他位置硬编码角色字符串，以免拼写错误引发难以排查的问题
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
