package com.inkisle.service;

import com.inkisle.entity.BookAuditRecord;

import java.util.List;
import java.util.Map;

/**
 * 审核记录表只留历史，作品是否上架以 book_info.audit_status 为准
 */
public interface BookAuditService {

    List<BookAuditRecord> listByStatus(Integer status);

    /** 后台列表分页。审核记录只增不删，一两年后全量返回会拖到秒级 */
    Map<String, Object> pageByStatus(Integer status, Integer pageNum, Integer pageSize);

    void pass(Long id, Long adminId);

    /** 驳回必须带原因，同时写到作品上，作者列表不必回查记录表 */
    void reject(Long id, String remark, Long adminId);

    /** 只有草稿、已驳回、已下架能提交，且至少要有一章 */
    void submit(Long bookId, Long authorId);

    /** 改完作品信息自动重送，不报错也不重复插记录 */
    void resubmitAfterEdit(Long bookId, Long authorId);

    void takeDown(Long bookId, String remark, Long adminId);

    void offlineByAuthor(Long bookId, Long authorId);

    void restore(Long bookId, Long adminId);

    Map<String, Object> listBooks(Integer auditStatus, Integer pageNum, Integer pageSize);

    Map<String, Object> preview(Long bookId);

    /** chapterId 必须属于该作品，否则预览接口能读任意章节 */
    String chapterContent(Long bookId, Long chapterId);

    /** 驳回意见只给作者本人看，所以要带归属校验 */
    BookAuditRecord latestAuditForAuthor(Long bookId, Long authorId);

    /** 作品被删时清掉它的审核记录，由书籍服务在删书时调过来 */
    void deleteByBookId(Long bookId);

}
