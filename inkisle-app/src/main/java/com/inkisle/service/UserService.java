package com.inkisle.service;

import com.inkisle.common.Result;
import com.inkisle.dto.PasswordDTO;
import com.inkisle.dto.UserProfileDTO;
import com.inkisle.dto.UserRegisterDTO;
import com.inkisle.entity.UserInfo;

import java.util.Map;

public interface UserService {

    UserInfo getById(Long id);

    Result<Void> register(UserRegisterDTO registerDTO);

    Result<Map<String, Object>> login(String username, String password);

    Map<String, Object> page(Integer pageNum, Integer pageSize);

    /** 1 封禁，0 正常 */
    void updateStatus(Long userId, Integer status);

    UserInfo updateProfile(Long userId, UserProfileDTO profileDTO);

    /** 要校验原密码，改完清登录态 */
    void changePassword(Long userId, PasswordDTO passwordDTO);

}
