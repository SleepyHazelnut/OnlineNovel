package com.inkisle.service;

import com.inkisle.common.BusinessException;
import com.inkisle.dto.PasswordDTO;
import com.inkisle.dto.UserProfileDTO;
import com.inkisle.entity.UserInfo;
import com.inkisle.support.BaseIntegrationTest;
import com.inkisle.util.Md5Util;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 账号设置
 * <p>
 * 改资料要保证空值落成 null 而不是空串，改密码要保证原密码校验真的生效——
 * 后者一旦漏了，拿到 token 的人可以不知道原密码就直接改掉
 */
@Transactional
class UserProfileServiceTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("保存资料时更新昵称、手机号与邮箱，空串存为 null")
    void updateProfileSavesFields() {
        long userId = insertUser("profile-save", 0);

        UserProfileDTO dto = new UserProfileDTO();
        dto.setNickName("新昵称");
        dto.setPhone("");
        dto.setEmail("reader@example.com");

        UserInfo updated = userService.updateProfile(userId, dto);

        assertThat(updated.getNickName()).isEqualTo("新昵称");
        assertThat(updated.getPhone()).isNull();
        assertThat(updated.getEmail()).isEqualTo("reader@example.com");
        // 返回体里不能带密码
        assertThat(updated.getPassword()).isNull();
    }

    @Test
    @DisplayName("昵称只有空格时拒绝保存")
    void blankNickNameIsRejected() {
        long userId = insertUser("profile-blank", 0);

        UserProfileDTO dto = new UserProfileDTO();
        dto.setNickName("   ");

        assertThatThrownBy(() -> userService.updateProfile(userId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("昵称不能为空");
    }

    @Test
    @DisplayName("原密码正确时改密码，库里存的是新密码的摘要")
    void changePassword_whenOldMatches() {
        long userId = insertUser("profile-pwd", 0);

        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword("123456");
        dto.setNewPassword("newpass123");

        userService.changePassword(userId, dto);

        String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM user_info WHERE id = ?", String.class, userId);
        assertThat(stored).isEqualTo(Md5Util.md5("newpass123"));
    }

    @Test
    @DisplayName("原密码不对时拒绝修改，库中密码保持不变")
    void changePassword_whenOldMismatch() {
        long userId = insertUser("profile-pwd-bad", 0);
        String before = jdbcTemplate.queryForObject(
                "SELECT password FROM user_info WHERE id = ?", String.class, userId);

        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword("wrong-password");
        dto.setNewPassword("newpass123");

        assertThatThrownBy(() -> userService.changePassword(userId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原密码");

        String after = jdbcTemplate.queryForObject(
                "SELECT password FROM user_info WHERE id = ?", String.class, userId);
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("新密码与原密码相同时拒绝")
    void changePassword_whenSame() {
        long userId = insertUser("profile-pwd-same", 0);

        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword("123456");
        dto.setNewPassword("123456");

        assertThatThrownBy(() -> userService.changePassword(userId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能与原密码相同");
    }

}
