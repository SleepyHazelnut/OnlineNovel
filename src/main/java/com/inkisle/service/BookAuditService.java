package com.inkisle.service;

import com.inkisle.entity.BookAuditRecord;

import java.util.List;

public interface BookAuditService {

    /**
     * 按状态查询审核记录，供管理端待审列表使用
     */
    List<BookAuditRecord> listByStatus(Integer status);

    /**
     * 审核通过
     */
    void pass(Long id, Long adminId);

    /**
     * 驳回，必须填写原因，否则作者无从判断需修改的内容
     */
    void reject(Long id, String remark, Long adminId);

    /**
     * 作者提交审核，作品上架前必须先经此流程
     */
    void submit(Long bookId, Long authorId);

    /**
     * 某本书最新的一条审核记录，用于判断作品是否对读者可见
     */
    BookAuditRecord latestAudit(Long bookId);

}
