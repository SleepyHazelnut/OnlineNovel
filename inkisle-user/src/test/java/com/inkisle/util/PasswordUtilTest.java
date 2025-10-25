package com.inkisle.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 密码格式迁移的边界
 * 最容易出事的两点：老账号登录不上、加了盐却还用「撞哈希」的方式比对
 */
class PasswordUtilTest {

    /** 123456 的 MD5，和老账号在库里的样子一致 */
    private static final String LEGACY_MD5 = "e10adc3949ba59abbe56e057f20f883e";

    @Test
    @DisplayName("新密码存成 BCrypt，同一个密码两次入库结果不同")
    void newPasswordIsSalted() {
        String first = PasswordUtil.encode("123456");
        String second = PasswordUtil.encode("123456");

        assertThat(first).startsWith("$2");
        assertThat(first).isNotEqualTo(second);
        assertThat(PasswordUtil.matches("123456", first)).isTrue();
        assertThat(PasswordUtil.matches("123457", first)).isFalse();
    }

    @Test
    @DisplayName("老的无盐 MD5 记录仍然能登录，并标记为待升级")
    void legacyHashStillWorks() {
        assertThat(PasswordUtil.matches("123456", LEGACY_MD5)).isTrue();
        assertThat(PasswordUtil.matches("1234567", LEGACY_MD5)).isFalse();
        assertThat(PasswordUtil.isLegacy(LEGACY_MD5)).isTrue();
    }

    @Test
    @DisplayName("已升级的记录不会被反复重算")
    void upgradedHashIsNotLegacy() {
        String hashed = PasswordUtil.encode("123456");

        assertThat(PasswordUtil.isLegacy(hashed)).isFalse();
    }

    @Test
    @DisplayName("空密码与空库值一律判失败，不能因为都是 null 就放行")
    void nullSafe() {
        assertThat(PasswordUtil.matches(null, LEGACY_MD5)).isFalse();
        assertThat(PasswordUtil.matches("123456", null)).isFalse();
        assertThat(PasswordUtil.isLegacy(null)).isFalse();
    }

}
