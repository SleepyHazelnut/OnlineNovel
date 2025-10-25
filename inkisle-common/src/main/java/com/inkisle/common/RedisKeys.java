package com.inkisle.common;

/**
 * 跨服务共用的 Redis key
 * 封禁状态由用户服务写、书籍服务读，key 拼错一边就等于没缓存，
 * 所以集中在这里，谁也别再手拼字符串
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 账号状态：0 正常、1 封禁、null 表示账号不存在 */
    public static String userStatus(String role, Long id) {
        return "user:status:" + role + ":" + id;
    }

}
