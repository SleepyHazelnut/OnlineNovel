package com.inkisle.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 摘要工具
 * 用户密码存储与上传文件的按内容去重均需计算 MD5，故统一抽取至此
 */
public class Md5Util {

    private Md5Util() {
    }

    /**
     * 计算字符串的 MD5 值，返回 32 位小写十六进制
     */
    public static String md5(String text) {
        return md5(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 MD5，用于上传文件的按内容去重
     */
    public static String md5(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(data);

            StringBuilder sb = new StringBuilder(32);
            for (byte b : bytes) {
                // byte 为有符号类型，直接转十六进制会得到负数，先 & 0xff 转成 0-255 再转换
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
