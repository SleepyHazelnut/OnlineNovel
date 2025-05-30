package com.inkisle.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 摘要工具
 */
public class Md5Util {

    private Md5Util() {
    }

    /**
     * 计算字符串的 MD5 值，返回 32 位小写十六进制
     */
    public static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(32);
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 自带 MD5，正常不会走到这里
            throw new IllegalStateException("当前环境不支持 MD5 算法", e);
        }
    }

}
