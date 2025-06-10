package com.inkisle.service;

import com.inkisle.common.Result;
import com.inkisle.entity.AuthorInfo;

import java.util.Map;

public interface AuthorService {

    /**
     * 作者注册
     */
    Result<Void> register(AuthorInfo authorInfo);

    /**
     * 作者登录，复用读者那套 JWT
     */
    Result<Map<String, Object>> login(String username, String password);

    AuthorInfo getById(Long id);

}
