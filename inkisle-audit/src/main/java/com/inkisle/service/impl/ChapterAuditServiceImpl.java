package com.inkisle.service.impl;

import com.inkisle.client.BookClient;
import com.inkisle.common.BusinessException;
import com.inkisle.common.ContentAuditStatus;
import com.inkisle.common.PageUtils;
import com.inkisle.dto.AuditStatusDTO;
import com.inkisle.entity.BookChapter;
import com.inkisle.service.ChapterAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 章节审核。只有三种状态，状态列在书籍服务，修改统一走内部接口
 */
@Service
public class ChapterAuditServiceImpl implements ChapterAuditService {

    private static final Logger log = LoggerFactory.getLogger(ChapterAuditServiceImpl.class);

    @Autowired
    private BookClient bookClient;

    @Override
    public Map<String, Object> pageByStatus(Integer auditStatus, Integer pageNum, Integer pageSize) {
        if (!ContentAuditStatus.isValid(auditStatus)) {
            throw new BusinessException("审核状态不对");
        }
        int page = PageUtils.pageNum(pageNum);
        int size = PageUtils.pageSize(pageSize, 20);
        return bookClient.listChaptersByStatus(auditStatus, PageUtils.offset(page, size), size);
    }

    @Override
    public void pass(Long chapterId, Long adminId) {
        BookChapter chapter = requirePending(chapterId);
        updateStatus(chapter.getId(), ContentAuditStatus.PASSED, null);
        log.info("章节审核通过 chapterId={} bookId={} adminId={}",
                chapter.getId(), chapter.getBookId(), adminId);
    }

    @Override
    public void reject(Long chapterId, String remark, Long adminId) {
        String reason = requireRemark(remark);
        BookChapter chapter = requirePending(chapterId);
        updateStatus(chapter.getId(), ContentAuditStatus.REJECTED, reason);
        log.info("章节驳回 chapterId={} bookId={} adminId={} reason={}",
                chapter.getId(), chapter.getBookId(), adminId, reason);
    }

    @Override
    public Map<String, Object> detail(Long chapterId) {
        BookChapter chapter = bookClient.getChapter(chapterId);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("chapter", chapter);
        Object content = bookClient.getChapterContent(chapterId).get("content");
        data.put("content", content == null ? "" : content);
        return data;
    }

    /**
     * 通过和驳回都不再清详情缓存：章节数、字数没变，
     * 驳回后的可见性由书籍服务判断
     */
    private void updateStatus(Long chapterId, ContentAuditStatus status, String remark) {
        AuditStatusDTO dto = new AuditStatusDTO();
        dto.setStatus(status.getCode());
        dto.setRemark(remark);
        bookClient.updateChapterStatus(chapterId, dto);
    }

    /**
     * 只有待审的章节需要处理。已通过或已驳回的再次提交，
     * 说明列表没刷新或有人重复操作，直接挡住而不是静默覆盖
     */
    private BookChapter requirePending(Long chapterId) {
        BookChapter chapter = bookClient.getChapter(chapterId);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        if (chapter.getAuditStatus() == null || chapter.getAuditStatus() != ContentAuditStatus.PENDING.getCode()) {
            throw new BusinessException("这一章已经审过了");
        }
        return chapter;
    }

    private String requireRemark(String remark) {
        if (remark == null || remark.trim().isEmpty()) {
            throw new BusinessException("驳回要写原因");
        }
        return remark.trim();
    }

}
