package com.inkisle.service;

import java.util.Map;

public interface ChapterAuditService {

    Map<String, Object> pageByStatus(Integer auditStatus, Integer pageNum, Integer pageSize);

    void pass(Long chapterId, Long adminId);

    /** 驳回要写原因，作者改完保存会重新回到待审 */
    void reject(Long chapterId, String remark, Long adminId);

    Map<String, Object> detail(Long chapterId);

}
