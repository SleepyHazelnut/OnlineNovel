package com.inkisle.service;

import com.inkisle.common.Result;
import com.inkisle.entity.AdminInfo;

import java.util.Map;

public interface AdminService {

    /**
     * 管理端登录。管理员使用独立数据表，不与读者、作者共用
     */
    Result<Map<String, Object>> login(String username, String password);

    AdminInfo getById(Long id);

}
