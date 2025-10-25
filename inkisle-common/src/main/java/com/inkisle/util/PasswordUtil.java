package com.inkisle.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码摘要
 *
 * 新密码一律 BCrypt：自带随机盐，同一个密码两次入库的结果不同，
 * 拿到库也反查不出常见密码。
 * 老数据是无盐 MD5，没法直接换 —— 换算法要么让所有人改一次密，
 * 要么一次性刷全表，前者影响用户，后者上线当天要停写。
 * 这里按哈希前缀识别，登录校验通过后顺手把这条记录换成 BCrypt，
 * 用户无感，存量在几周内自然迁完
 */
public final class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /** BCrypt 的哈希都以 $2a$ / $2b$ / $2y$ 开头，MD5 是 32 位十六进制，不会撞 */
    private static final String BCRYPT_PREFIX = "$2";

    private PasswordUtil() {
    }

    /** 新密码入库前调它 */
    public static String encode(String raw) {
        return ENCODER.encode(raw);
    }

    /** 校验密码，新旧格式都认 */
    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        if (isLegacy(stored)) {
            return Md5Util.md5(raw).equals(stored);
        }
        return ENCODER.matches(raw, stored);
    }

    /** 这条记录还是旧格式，登录成功后应当重算并写回 */
    public static boolean isLegacy(String stored) {
        return stored != null && !stored.startsWith(BCRYPT_PREFIX);
    }

}
