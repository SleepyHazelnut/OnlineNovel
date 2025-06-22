package com.inkisle.service;

import com.inkisle.common.Result;
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

}
