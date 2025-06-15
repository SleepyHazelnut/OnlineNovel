package com.inkisle.service;

import com.inkisle.common.Result;
import com.inkisle.entity.AuthorInfo;

import java.util.List;
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

    /**
     * 按资质审核状态查作者，管理端用
     */
    List<AuthorInfo> listByAuditStatus(Integer auditStatus);

    /**
     * 资质审核，1 通过 2 驳回
     */
    void audit(Long authorId, Integer status, String remark);

}
