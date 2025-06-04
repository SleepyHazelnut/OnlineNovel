package com.inkisle.service;

import com.inkisle.common.Result;
import com.inkisle.entity.UserInfo;

import java.util.List;

public interface UserService {

    /**
     * 查询全部用户
     */
    List<UserInfo> listAll();

    UserInfo getById(Long id);

    /**
     * 用户注册
     */
    Result register(UserInfo userInfo);

    /**
     * 用户登录，成功后返回 token
     */
    Result login(String username, String password);

}
