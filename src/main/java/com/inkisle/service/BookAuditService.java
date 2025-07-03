package com.inkisle.service;

import com.inkisle.entity.BookAuditRecord;

import java.util.List;
import java.util.Map;

/**
 * 作品审核
 * 审核记录表只留历史，作品当前是否上架以 book_info.audit_status 为准，
 * 两者在同一次流转里一起写，避免出现「记录已通过、书还不可见」这类不一致
 */
public interface BookAuditService {

    /**
     * 按状态查询审核记录，供管理端待审列表使用
     */
    List<BookAuditRecord> listByStatus(Integer status);

    /**
     * 审核通过：记录置为通过，作品置为已上架，读者立即可见
     */
    void pass(Long id, Long adminId);

    /**
     * 驳回，必须填写原因，否则作者无从判断需修改的内容
     * 驳回原因同时写到作品上，作者列表不查记录表也能看到
     */
    void reject(Long id, String remark, Long adminId);

    /**
     * 作者提交审核。仅草稿、已驳回、已下架的作品可提交，
     * 且必须至少有一章内容，否则通过后读者点开是空目录
     */
    void submit(Long bookId, Long authorId);

    /**
     * 作品信息变更后自动重新送审，由保存作品的流程调用
     * 与手动提交的区别是不报错、不重复插入待审记录：作者只是改了个简介，
     * 不该被「已经提交过了」挡住
     */
    void resubmitAfterEdit(Long bookId, Long authorId);

    /**
     * 管理员下架已上架的作品。必须填写原因，作者需要知道为什么被下架
     */
    void takeDown(Long bookId, String remark, Long adminId);

    /**
     * 作者主动下架自己的作品。与管理员下架的区别是没有审核意见，
     * 作品状态同样是「已下架」，之后可重新提交审核
     */
    void offlineByAuthor(Long bookId, Long authorId);

    /**
     * 恢复上架，仅对已下架的作品有效。恢复后直接可见，不再走一遍审核
     */
    void restore(Long bookId, Long adminId);

    /**
     * 管理端按上架状态查书，分页。状态只接受「已通过」与「已下架」两种
     */
    Map<String, Object> listBooks(Integer auditStatus, Integer pageNum, Integer pageSize);

    /**
     * 审核预览：作品信息 + 章节目录。审核不能只看书名，
     * 且待审作品的章节走不了读者端接口（那里有上架校验）
     */
    Map<String, Object> preview(Long bookId);

    /**
     * 预览某一章正文。chapterId 必须属于 bookId，防止借预览接口读任意章节
     */
    String chapterContent(Long bookId, Long chapterId);

    /**
     * 作者查询自己作品的最新审核记录。驳回意见只该让作者本人看到，
     * 故不能直接暴露不带归属校验的查询
     */
    BookAuditRecord latestAuditForAuthor(Long bookId, Long authorId);

}
