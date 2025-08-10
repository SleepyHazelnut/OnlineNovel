package com.inkisle.service;

import com.inkisle.common.Result;
import com.inkisle.dto.AuthorRegisterDTO;
import com.inkisle.entity.AuthorInfo;

import java.util.List;
import java.util.Map;

public interface AuthorService {

    /**
     * 作者注册，注册完资质是待审，得管理端过了才行
     */
    Result<Void> register(AuthorRegisterDTO registerDTO);

    /**
     * 作者登录，token 跟读者用同一套 JWT，就角色不一样
     */
    Result<Map<String, Object>> login(String username, String password);

    AuthorInfo getById(Long id);

    /**
     * 按资质状态查作者，管理端筛待审列表用
     */
    List<AuthorInfo> listByAuditStatus(Integer auditStatus);

    /**
     * 资质审核，1 通过 2 驳回，驳回必须写原因
     */
    void audit(Long authorId, Integer status, String remark);

}
