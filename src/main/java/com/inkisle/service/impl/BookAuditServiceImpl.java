package com.inkisle.service.impl;

import com.inkisle.common.BookAuditStatus;
import com.inkisle.common.BusinessException;
import com.inkisle.common.PageUtils;
import com.inkisle.entity.BookAuditRecord;
import com.inkisle.entity.BookChapter;
import com.inkisle.entity.BookContent;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.BookAuditRecordMapper;
import com.inkisle.mapper.BookChapterMapper;
import com.inkisle.mapper.BookContentMapper;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.service.BookAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 作品审核
 * <p>
 * 一次流转要落两处：审核记录表留历史，book_info.audit_status 表示作品当前状态
 * 读者只看后者，管理端的审核列表看前者，两者必须同事务写入，
 * 否则会出现「记录已通过、作品仍不可见」这种对不上的情况
 * <p>
 * 记录表的状态是 0待审 / 1通过 / 2驳回，作品状态另有「未提交」「已下架」，
 * 编号不一致，故统一用 BookAuditStatus 表达，不直接互传数字
 */
@Service
public class BookAuditServiceImpl implements BookAuditService {

    private static final Logger log = LoggerFactory.getLogger(BookAuditServiceImpl.class);

    /** 审核记录：待审 */
    private static final int RECORD_PENDING = 0;

    /** 审核记录：通过 */
    private static final int RECORD_PASS = 1;

    /** 审核记录：驳回 */
    private static final int RECORD_REJECT = 2;

    @Autowired
    private BookAuditRecordMapper bookAuditRecordMapper;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @Autowired
    private BookContentMapper bookContentMapper;

    @Autowired
    private CacheManager cacheManager;

    @Override
    public List<BookAuditRecord> listByStatus(Integer status) {
        return bookAuditRecordMapper.selectByStatus(status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pass(Long id, Long adminId) {
        BookAuditRecord record = checkPending(id);
        // 提交后作者仍可能把章节删光，通过前再确认一次，避免上架一本空书
        requireChapter(record.getBookId(), "作品没有章节，不能通过审核");

        BookAuditRecord update = new BookAuditRecord();
        update.setId(record.getId());
        update.setStatus(RECORD_PASS);
        update.setAuditAdminId(adminId);
        update.setAuditTime(LocalDateTime.now());
        bookAuditRecordMapper.updateAudit(update);

        markBook(record.getBookId(), BookAuditStatus.PASSED, null);
        // 审核员看的是整本书，章节不必再逐条审一遍；
        // 之后新增或修改的章节仍会回到待审，由章节审核单独处理
        bookChapterMapper.passAllByBookId(record.getBookId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String remark, Long adminId) {
        // 驳回必须有原因，为空时直接拒绝
        String reason = requireRemark(remark, "驳回要写原因");
        BookAuditRecord record = checkPending(id);

        BookAuditRecord update = new BookAuditRecord();
        update.setId(record.getId());
        update.setStatus(RECORD_REJECT);
        update.setRemark(reason);
        update.setAuditAdminId(adminId);
        update.setAuditTime(LocalDateTime.now());
        bookAuditRecordMapper.updateAudit(update);

        markBook(record.getBookId(), BookAuditStatus.REJECTED, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long bookId, Long authorId) {
        BookInfo book = requireOwnBook(bookId, authorId);

        Integer current = book.getAuditStatus();
        if (current != null && current == BookAuditStatus.PENDING.getCode()) {
            throw new BusinessException("已经提交过了，等审核结果");
        }
        if (current != null && current == BookAuditStatus.PASSED.getCode()) {
            // 已上架的作品不需要手动送审，改展示信息会自动触发
            throw new BusinessException("作品已在架上，修改书名、简介或封面后会自动重新送审");
        }
        if (!BookAuditStatus.canSubmit(current)) {
            throw new BusinessException("当前状态不能提交审核");
        }

        requireChapter(bookId, "还没有章节，先写点内容再提交");
        insertPendingRecord(bookId, authorId);
        markBook(bookId, BookAuditStatus.PENDING, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmitAfterEdit(Long bookId, Long authorId) {
        // 已有待审记录时不再插入：作者连续改两次，管理端只该看到一条
        if (bookAuditRecordMapper.countPending(bookId) == 0) {
            insertPendingRecord(bookId, authorId);
        }
        markBook(bookId, BookAuditStatus.PENDING, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void takeDown(Long bookId, String remark, Long adminId) {
        String reason = requireRemark(remark, "下架要写原因");
        BookInfo book = bookInfoMapper.selectById(bookId);
        if (book == null) {
            throw new BusinessException("作品不存在");
        }
        if (!BookAuditStatus.isVisible(book.getAuditStatus())) {
            throw new BusinessException("只有已上架的作品可以下架");
        }

        markBook(bookId, BookAuditStatus.OFFLINE, reason);
        // 记录表不改：它记的是作者送审的那一次，下架是管理员动作，没有对应的送审，
        // 硬塞一条「驳回」会让作者看到一条并不存在的审核结论
        // 操作人先记在应用日志里，审计需求变重时再补独立的操作日志表
        log.info("作品下架 bookId={} adminId={} reason={}", bookId, adminId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offlineByAuthor(Long bookId, Long authorId) {
        BookInfo book = requireOwnBook(bookId, authorId);
        if (!BookAuditStatus.isVisible(book.getAuditStatus())) {
            throw new BusinessException("只有已上架的作品可以下架");
        }

        // 审核意见写「作者自主下架」：作者列表上这一列会展示，
        // 不写的话作者过几天自己也分不清是管理员下架的还是自己下架的
        markBook(bookId, BookAuditStatus.OFFLINE, "作者自主下架");
        // 记录表不动，原因与管理员下架一致：它不是一次送审，没有审核结论可记
        log.info("作者自主下架 bookId={} authorId={}", bookId, authorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long bookId, Long adminId) {        BookInfo book = bookInfoMapper.selectById(bookId);
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
        // 管理端只提供两个入口：已通过的书用于下架，已下架的书用于恢复
        // 待审的书走审核记录列表，未提交和已驳回的书不该由管理员直接处理
        if (auditStatus == null
                || (auditStatus != BookAuditStatus.PASSED.getCode()
                && auditStatus != BookAuditStatus.OFFLINE.getCode())) {
            throw new BusinessException("只能查已上架或已下架的作品");
        }
        int page = PageUtils.pageNum(pageNum);
        int size = PageUtils.pageSize(pageSize, 20);

        Map<String, Object> data = new HashMap<>();
        data.put("list", bookInfoMapper.selectByAuditStatus(auditStatus, PageUtils.offset(page, size), size));
        data.put("total", bookInfoMapper.countByAuditStatus(auditStatus));
        return data;
    }

    @Override
    public Map<String, Object> preview(Long bookId) {
        BookInfo book = bookInfoMapper.selectById(bookId);
        if (book == null) {
            throw new BusinessException("作品不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("book", book);
        // 审核要看全部章节，包括待审与驳回的
        data.put("chapters", bookChapterMapper.selectAllByBookId(bookId));
        return data;
    }

    @Override
    public String chapterContent(Long bookId, Long chapterId) {
        BookChapter chapter = bookChapterMapper.selectById(chapterId);
        if (chapter == null || !chapter.getBookId().equals(bookId)) {
            throw new BusinessException("章节不属于这本书");
        }
        BookContent content = bookContentMapper.selectByChapterId(chapterId);
        return content == null ? "" : content.getContent();
    }

    @Override
    public BookAuditRecord latestAuditForAuthor(Long bookId, Long authorId) {
        BookInfo book = requireOwnBook(bookId, authorId);
        return bookAuditRecordMapper.selectLatestByBookId(book.getId());
    }

    /**
     * 取出待审记录并拦截已审核的记录；通过与驳回均经此校验，避免两处重复判断
     */
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

    /**
     * 作品上架状态的唯一写入口，同时刷新更新时间
     * remark 传 null 即清空，审核通过时会顺带抹掉上一次的驳回意见
     * <p>
     * 顺带清掉详情缓存：缓存里存的是整个 BookInfo，
     * 不清理的话最长 30 分钟内读到的还是旧的上架状态
     */
    private void markBook(Long bookId, BookAuditStatus status, String remark) {
        bookInfoMapper.updateAuditStatus(bookId, status.getCode(), remark, LocalDateTime.now());
        Cache cache = cacheManager.getCache("book");
        if (cache != null) {
            cache.evict(bookId);
        }
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
        BookInfo book = bookInfoMapper.selectById(bookId);
        if (book == null) {
            throw new BusinessException("作品不存在");
        }
        if (!authorId.equals(book.getAuthorId())) {
            throw new BusinessException("只能操作自己的作品");
        }
        return book;
    }

    private void requireChapter(Long bookId, String message) {
        if (bookChapterMapper.selectAllByBookId(bookId).isEmpty()) {
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
