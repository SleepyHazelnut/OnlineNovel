package com.inkisle.service;

import com.inkisle.common.Result;
import com.inkisle.dto.PasswordDTO;
import com.inkisle.dto.UserProfileDTO;
import com.inkisle.dto.UserRegisterDTO;
import com.inkisle.entity.UserInfo;

import java.util.Map;

public interface UserService {

    UserInfo getById(Long id);

    /**
     * 用户注册，用户名需先查重
     */
    Result<Void> register(UserRegisterDTO registerDTO);

    /**
     * 用户登录，成功时返回 token 与用户信息
     */
    Result<Map<String, Object>> login(String username, String password);

    /**
     * 用户分页列表，供管理端使用
     */
    Map<String, Object> page(Integer pageNum, Integer pageSize);

    /**
     * 封禁 / 解封：1 封禁，0 正常
     */
    void updateStatus(Long userId, Integer status);

    /**
     * 修改昵称、手机号、邮箱、头像，返回更新后的用户信息（不含密码）
     */
    UserInfo updateProfile(Long userId, UserProfileDTO profileDTO);

    /**
     * 修改密码，须校验原密码
     */
    void changePassword(Long userId, PasswordDTO passwordDTO);

}
