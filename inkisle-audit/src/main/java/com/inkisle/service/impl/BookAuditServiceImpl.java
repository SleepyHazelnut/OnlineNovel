package com.inkisle.service.impl;

import com.inkisle.client.BookClient;
import com.inkisle.common.BookAuditStatus;
import com.inkisle.common.BusinessException;
import com.inkisle.common.PageUtils;
import com.inkisle.dto.AuditStatusDTO;
import com.inkisle.entity.BookAuditRecord;
import com.inkisle.entity.BookChapter;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.BookAuditRecordMapper;
import com.inkisle.service.BookAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审核。记录表在本服务，作品状态在书籍服务，改状态要发 Feign 过去
 * 事务只覆盖本地这张表，远程调用失败不会跟着回滚，
 * 所以先写记录再改状态。改状态失败会留下一条待审记录，重新操作一次会覆盖
 */
@Service
public class BookAuditServiceImpl implements BookAuditService {

    private static final Logger log = LoggerFactory.getLogger(BookAuditServiceImpl.class);

    private static final int RECORD_PENDING = 0;

    private static final int RECORD_PASS = 1;

    private static final int RECORD_REJECT = 2;

    @Autowired
    private BookAuditRecordMapper bookAuditRecordMapper;

    @Autowired
    private BookClient bookClient;

    @Override
    public List<BookAuditRecord> listByStatus(Integer status) {
        return bookAuditRecordMapper.selectByStatus(status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pass(Long id, Long adminId) {
        BookAuditRecord record = checkPending(id);
        // 作者提交之后可能又把章节删光，通过前再确认一次
        requireChapter(record.getBookId(), "作品没有章节，不能通过审核");

        markRecord(id, RECORD_PASS, null, adminId);
        markBook(record.getBookId(), BookAuditStatus.PASSED, null);
        // 审核看的是整本书，章节不再逐条审；之后新增或修改的章节会重新回到待审
        bookClient.passAllChapters(record.getBookId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String remark, Long adminId) {
        String reason = requireRemark(remark, "驳回要写原因");
        BookAuditRecord record = checkPending(id);

        markRecord(id, RECORD_REJECT, reason, adminId);
        markBook(record.getBookId(), BookAuditStatus.REJECTED, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long bookId, Long authorId) {
        BookInfo book = requireOwnBook(bookId, authorId);

        Integer current = book.getAuditStatus();
        if (current != null && current == BookAuditStatus.PENDING.getCode()) {
            throw new BusinessException("作品已在审核中，请等待结果");
        }
        if (current != null && current == BookAuditStatus.PASSED.getCode()) {
            throw new BusinessException("作品已在架上，修改书名、简介或封面后会自动重新送审");
        }
        if (!BookAuditStatus.canSubmit(current)) {
            throw new BusinessException("当前状态不能提交审核");
        }

        requireChapter(bookId, "至少发布一章后才能提交审核");
        // 先改状态再插记录：状态没改成说明别人已经提交过，此时不该再插一条待审记录
        if (bookClient.markPending(bookId) == 0) {
            throw new BusinessException("作品已在审核中，请等待结果");
        }
        insertPendingRecord(bookId, authorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmitAfterEdit(Long bookId, Long authorId) {
        // 已经有待审记录就不再插入，作者连改两次，管理端只该看到一条
        if (bookAuditRecordMapper.countPending(bookId) == 0) {
            insertPendingRecord(bookId, authorId);
        }
        markBook(bookId, BookAuditStatus.PENDING, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void takeDown(Long bookId, String remark, Long adminId) {
        String reason = requireRemark(remark, "下架要写原因");
        BookInfo book = bookClient.getBook(bookId);
        if (book == null) {
            throw new BusinessException("作品不存在");
        }
        if (!BookAuditStatus.isVisible(book.getAuditStatus())) {
            throw new BusinessException("只有已上架的作品可以下架");
        }

        markBook(bookId, BookAuditStatus.OFFLINE, reason);
        // 记录表不动：下架不是一次送审，硬塞一条驳回会让作者看到不存在的结论
        log.info("作品下架 bookId={} adminId={} reason={}", bookId, adminId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offlineByAuthor(Long bookId, Long authorId) {
        BookInfo book = requireOwnBook(bookId, authorId);
        if (!BookAuditStatus.isVisible(book.getAuditStatus())) {
            throw new BusinessException("只有已上架的作品可以下架");
        }

        // 作者列表会展示这一列，不写作者自己都分不清是谁下架的
        markBook(bookId, BookAuditStatus.OFFLINE, "作者自主下架");
        log.info("作者自主下架 bookId={} authorId={}", bookId, authorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long bookId, Long adminId) {
        BookInfo book = bookClient.getBook(bookId);
        if (book == null) {
            throw new BusinessException("作品不存在");
        }
        if (book.getAuditStatus() == null || book.getAuditStatus() != BookAuditStatus.OFFLINE.getCode()) {
            throw new BusinessException("只有已下架的作品可以恢复上架");
        }

        markBook(bookId, BookAuditStatus.PASSED, null);
        log.info("作品恢复上架 bookId={} adminId={}", bookId, adminId);
    }

    @Override
    public Map<String, Object> listBooks(Integer auditStatus, Integer pageNum, Integer pageSize) {
        // 待审的走审核记录列表，这里只处理已上架和已下架的书
        if (auditStatus == null
                || (auditStatus != BookAuditStatus.PASSED.getCode()
                && auditStatus != BookAuditStatus.OFFLINE.getCode())) {
            throw new BusinessException("只能查已上架或已下架的作品");
        }
        int page = PageUtils.pageNum(pageNum);
        int size = PageUtils.pageSize(pageSize, 20);
        return bookClient.listByAuditStatus(auditStatus, PageUtils.offset(page, size), size);
    }

    @Override
    public Map<String, Object> preview(Long bookId) {
        BookInfo book = bookClient.getBook(bookId);
        if (book == null) {
            throw new BusinessException("作品不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("book", book);
        data.put("chapters", bookClient.listChapters(bookId));
        return data;
    }

    @Override
    public String chapterContent(Long bookId, Long chapterId) {
        BookChapter chapter = bookClient.getChapter(chapterId);
        if (chapter == null || !chapter.getBookId().equals(bookId)) {
            throw new BusinessException("章节不属于这本书");
        }
        Map<String, Object> data = bookClient.getChapterContent(chapterId);
        Object content = data == null ? null : data.get("content");
        return content == null ? "" : content.toString();
    }

    @Override
    public BookAuditRecord latestAuditForAuthor(Long bookId, Long authorId) {
        BookInfo book = requireOwnBook(bookId, authorId);
        return bookAuditRecordMapper.selectLatestByBookId(book.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByBookId(Long bookId) {
        bookAuditRecordMapper.deleteByBookId(bookId);
    }

    /** 通过和驳回都先走这里，已审过的记录直接挡掉 */
    private BookAuditRecord checkPending(Long id) {
        BookAuditRecord record = bookAuditRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("审核记录不存在");
        }
        if (record.getStatus() == null || record.getStatus() != RECORD_PENDING) {
            throw new BusinessException("这条已经审过了");
        }
        return record;
    }

    private void markRecord(Long id, int status, String remark, Long adminId) {
        BookAuditRecord update = new BookAuditRecord();
        update.setId(id);
        update.setStatus(status);
        update.setRemark(remark);
        update.setAuditAdminId(adminId);
        update.setAuditTime(LocalDateTime.now());
        bookAuditRecordMapper.updateAudit(update);
    }

    /** 作品状态的唯一写入口。remark 传 null 即清空，通过时同时清除上次的驳回意见 */
    private void markBook(Long bookId, BookAuditStatus status, String remark) {
        AuditStatusDTO dto = new AuditStatusDTO();
        dto.setStatus(status.getCode());
        dto.setRemark(remark);
        bookClient.updateBookStatus(bookId, dto);
    }

    private void insertPendingRecord(Long bookId, Long authorId) {
        BookAuditRecord record = new BookAuditRecord();
        record.setBookId(bookId);
        record.setAuthorId(authorId);
        record.setStatus(RECORD_PENDING);
        record.setSubmitTime(LocalDateTime.now());
        bookAuditRecordMapper.insert(record);
    }

    private BookInfo requireOwnBook(Long bookId, Long authorId) {
        BookInfo book = bookClient.getBook(bookId);
        if (book == null) {
            throw new BusinessException("作品不存在");
        }
        if (!authorId.equals(book.getAuthorId())) {
            throw new BusinessException("只能操作自己的作品");
        }
        return book;
    }

    private void requireChapter(Long bookId, String message) {
        if (bookClient.listChapters(bookId).isEmpty()) {
            throw new BusinessException(message);
        }
    }

    private String requireRemark(String remark, String message) {
        if (remark == null || remark.trim().isEmpty()) {
            throw new BusinessException(message);
        }
        return remark.trim();
    }

}
