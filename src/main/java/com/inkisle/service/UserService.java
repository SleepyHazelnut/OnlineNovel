package com.inkisle.service;

import com.inkisle.common.Result;
import com.inkisle.dto.UserRegisterDTO;
import com.inkisle.entity.UserInfo;

import java.util.Map;

public interface UserService {

    UserInfo getById(Long id);

    /**
     * 用户注册
     */
    Result<Void> register(UserRegisterDTO registerDTO);

    /**
     * 用户登录，成功后返回 token 和用户信息
     */
    Result<Map<String, Object>> login(String username, String password);

    /**
     * 用户分页列表，管理端用
     */
    Map<String, Object> page(Integer pageNum, Integer pageSize);

    /**
     * 封禁 / 解封，1 封禁 0 正常
     */
    void updateStatus(Long userId, Integer status);

}
