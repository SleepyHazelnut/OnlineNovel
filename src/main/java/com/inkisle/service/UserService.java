package com.inkisle.service;

import com.inkisle.entity.UserInfo;

import java.util.List;

public interface UserService {

    /**
     * 查询全部用户
     */
    List<UserInfo> listAll();

}
