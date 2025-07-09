package com.inkisle.service;

import com.inkisle.common.Result;
import com.inkisle.dto.AuthorRegisterDTO;
import com.inkisle.entity.AuthorInfo;

import java.util.List;
import java.util.Map;

public interface AuthorService {

    /**
     * 作者注册，注册后资质为待审状态，需管理端审核通过
     */
    Result<Void> register(AuthorRegisterDTO registerDTO);

    /**
     * 作者登录，token 复用读者的 JWT 方案，仅角色不同
     */
    Result<Map<String, Object>> login(String username, String password);

    AuthorInfo getById(Long id);

    /**
     * 按资质审核状态查询作者，供管理端筛选待审列表使用
     */
    List<AuthorInfo> listByAuditStatus(Integer auditStatus);

    /**
     * 资质审核，1 通过 2 驳回，驳回必须填写原因
     */
    void audit(Long authorId, Integer status, String remark);

}
