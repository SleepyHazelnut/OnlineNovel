package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.entity.BookAuditRecord;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.BookAuditRecordMapper;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.service.BookAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作品审核
 * 每次提交向审核记录表插入一条，新记录为待审状态，
 * 管理员的通过或驳回仅更新该条记录的 status
 */
@Service
public class BookAuditServiceImpl implements BookAuditService {

    /** 待审 */
    private static final int STATUS_PENDING = 0;

    /** 通过 */
    private static final int STATUS_PASS = 1;

    /** 驳回 */
    private static final int STATUS_REJECT = 2;

    @Autowired
    private BookAuditRecordMapper bookAuditRecordMapper;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Override
    public List<BookAuditRecord> listByStatus(Integer status) {
        return bookAuditRecordMapper.selectByStatus(status);
    }

    @Override
    public void pass(Long id, Long adminId) {
        BookAuditRecord record = checkPending(id);

        BookAuditRecord update = new BookAuditRecord();
        update.setId(record.getId());
        update.setStatus(STATUS_PASS);
        update.setAuditAdminId(adminId);
        update.setAuditTime(LocalDateTime.now());
        bookAuditRecordMapper.updateAudit(update);
    }

    @Override
    public void reject(Long id, String remark, Long adminId) {
        // 驳回必须有原因，为空时直接拒绝
        if (remark == null || remark.trim().isEmpty()) {
            throw new BusinessException("驳回要写原因");
        }
        BookAuditRecord record = checkPending(id);

        BookAuditRecord update = new BookAuditRecord();
        update.setId(record.getId());
        update.setStatus(STATUS_REJECT);
        update.setRemark(remark);
        update.setAuditAdminId(adminId);
        update.setAuditTime(LocalDateTime.now());
        bookAuditRecordMapper.updateAudit(update);
    }

    @Override
    public void submit(Long bookId, Long authorId) {
        BookInfo book = bookInfoMapper.selectById(bookId);
        if (book == null) {
            throw new BusinessException("作品不存在");
        }
        if (!authorId.equals(book.getAuthorId())) {
            throw new BusinessException("只能提交自己的作品");
        }
        // 已存在待审记录时不再插入，否则管理端列表会出现重复记录
        if (bookAuditRecordMapper.countPending(bookId) > 0) {
            throw new BusinessException("已经提交过了，等审核结果");
        }

        BookAuditRecord record = new BookAuditRecord();
        record.setBookId(bookId);
        record.setAuthorId(authorId);
        record.setStatus(STATUS_PENDING);
        record.setSubmitTime(LocalDateTime.now());
        bookAuditRecordMapper.insert(record);
    }

    @Override
    public BookAuditRecord latestAudit(Long bookId) {
        return bookAuditRecordMapper.selectLatestByBookId(bookId);
    }

    @Override
    public BookAuditRecord latestAuditForAuthor(Long bookId, Long authorId) {
        BookInfo book = bookInfoMapper.selectById(bookId);
        if (book == null) {
            throw new BusinessException("作品不存在");
        }
        if (!authorId.equals(book.getAuthorId())) {
            throw new BusinessException("只能查看自己作品的审核结果");
        }
        return bookAuditRecordMapper.selectLatestByBookId(bookId);
    }

    /**
     * 取出待审记录并拦截已审核的记录；通过与驳回均经此校验，避免两处重复判断
     */
    private BookAuditRecord checkPending(Long id) {
        BookAuditRecord record = bookAuditRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("审核记录不存在");
        }
        if (record.getStatus() == null || record.getStatus() != STATUS_PENDING) {
            throw new BusinessException("这条已经审过了");
        }
        return record;
    }

}
