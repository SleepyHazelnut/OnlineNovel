package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.common.ContentAuditStatus;
import com.inkisle.common.PageUtils;
import com.inkisle.entity.BookChapter;
import com.inkisle.entity.BookContent;
import com.inkisle.mapper.BookChapterMapper;
import com.inkisle.mapper.BookContentMapper;
import com.inkisle.service.ChapterAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 章节审核。状态只有三态，改这一列统一走 updateAuditStatus
 */
@Service
public class ChapterAuditServiceImpl implements ChapterAuditService {

    private static final Logger log = LoggerFactory.getLogger(ChapterAuditServiceImpl.class);

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @Autowired
    private BookContentMapper bookContentMapper;

    @Autowired
    private CacheManager cacheManager;

    @Override
    public Map<String, Object> pageByStatus(Integer auditStatus, Integer pageNum, Integer pageSize) {
        if (!ContentAuditStatus.isValid(auditStatus)) {
            throw new BusinessException("审核状态不对");
        }
        int page = PageUtils.pageNum(pageNum);
        int size = PageUtils.pageSize(pageSize, 20);

        Map<String, Object> data = new HashMap<>();
        data.put("list", bookChapterMapper.selectPageByStatus(
                auditStatus, PageUtils.offset(page, size), size));
        data.put("total", bookChapterMapper.countByStatus(auditStatus));
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pass(Long chapterId, Long adminId) {
        BookChapter chapter = requirePending(chapterId);
        bookChapterMapper.updateAuditStatus(chapter.getId(), ContentAuditStatus.PASSED.getCode(), null);
        // 章节数、字数没变，但详情页缓存里没有章节相关字段，这里不必清；
        // 真正需要清的是下面驳回的情况——驳回不影响读者可见性，同样不用清
        log.info("章节审核通过 chapterId={} bookId={} adminId={}",
                chapter.getId(), chapter.getBookId(), adminId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long chapterId, String remark, Long adminId) {
        String reason = requireRemark(remark);
        BookChapter chapter = requirePending(chapterId);
        bookChapterMapper.updateAuditStatus(chapter.getId(), ContentAuditStatus.REJECTED.getCode(), reason);
        // 章节被驳回后立刻从读者端目录消失，作品详情缓存里若带了章节统计需要刷新
        Cache cache = cacheManager.getCache("book");
        if (cache != null) {
            cache.evict(chapter.getBookId());
        }
        log.info("章节驳回 chapterId={} bookId={} adminId={} reason={}",
                chapter.getId(), chapter.getBookId(), adminId, reason);
    }

    @Override
    public Map<String, Object> detail(Long chapterId) {
        BookChapter chapter = bookChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        BookContent content = bookContentMapper.selectByChapterId(chapterId);

        Map<String, Object> data = new HashMap<>();
        data.put("chapter", chapter);
        data.put("content", content == null ? "" : content.getContent());
        return data;
    }

    /**
     * 只有待审的章节需要处理。已通过或已驳回的再点一次，
     * 说明列表没刷新或有人重复操作，直接挡住而不是静默覆盖
     */
    private BookChapter requirePending(Long chapterId) {
        BookChapter chapter = bookChapterMapper.selectById(chapterId);
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
