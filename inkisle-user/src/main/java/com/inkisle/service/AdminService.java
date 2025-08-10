package com.inkisle.service;

import com.inkisle.common.Result;
import com.inkisle.entity.AdminInfo;

import java.util.Map;

public interface AdminService {

    /**
     * 管理端登录。管理员单独一张表，不跟读者作者混
     */
    Result<Map<String, Object>> login(String username, String password);

    AdminInfo getById(Long id);

}
