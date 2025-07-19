package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.common.ContentAuditStatus;
import com.inkisle.common.PageUtils;
import com.inkisle.entity.BookComment;
import com.inkisle.mapper.BookCommentMapper;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.service.BookCommentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评论两级：一级的 parent_id 为 0，回复挂在一级评论下面
 */
@Service
public class BookCommentServiceImpl implements BookCommentService {

    private static final Logger log = LoggerFactory.getLogger(BookCommentServiceImpl.class);

    /** 一级评论的 parent_id 约定为 0 */
    private static final long ROOT_ID = 0L;

    /** 与表里的 varchar(500) 对齐 */
    private static final int MAX_CONTENT = 500;

    @Autowired
    private BookCommentMapper bookCommentMapper;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Autowired
    private CacheManager cacheManager;

    @Override
    public List<BookComment> listByBookId(Long bookId, Long viewerId) {
        List<BookComment> all = bookCommentMapper.selectByBookId(bookId, viewerId);

        List<BookComment> roots = new ArrayList<>();
        // 一次分组，避免每条一级评论都去遍历全表
        Map<Long, List<BookComment>> childrenByParent = new HashMap<>();
        for (BookComment comment : all) {
            // 历史数据里 parent_id 可能是 null，一并按一级处理
            if (comment.getParentId() == null || comment.getParentId() == ROOT_ID) {
                roots.add(comment);
                continue;
            }
            childrenByParent.computeIfAbsent(comment.getParentId(), key -> new ArrayList<>()).add(comment);
        }

        for (BookComment root : roots) {
            List<BookComment> children = childrenByParent.get(root.getId());
            root.setChildren(children == null ? new ArrayList<>() : children);
        }
        return roots;
    }

    @Override
    @Transactional
    public void add(BookComment comment) {
        comment.setContent(normalizeContent(comment.getContent()));
        if (comment.getParentId() == null) {
            comment.setParentId(ROOT_ID);
        }

        if (comment.getParentId() != ROOT_ID) {
            BookComment parent = bookCommentMapper.selectById(comment.getParentId());
            if (parent == null) {
                throw new BusinessException("要回复的评论不存在");
            }
            if (!parent.getBookId().equals(comment.getBookId())) {
                throw new BusinessException("要回复的评论不属于这本书");
            }
            // 只留两级：对回复再回复时，挂到它所属的一级评论下
            if (parent.getParentId() != ROOT_ID) {
                comment.setParentId(parent.getParentId());
            }
        }

        bookCommentMapper.insert(comment);
        refreshCommentCount(comment.getBookId());
    }

    @Override
    public Map<String, Object> pageByUser(Long userId, Integer pageNum, Integer pageSize) {
        int num = PageUtils.pageNum(pageNum);
        int size = PageUtils.pageSize(pageSize, 10);
        Map<String, Object> data = new HashMap<>();
        data.put("list", bookCommentMapper.selectByUserId(userId, PageUtils.offset(num, size), size));
        data.put("total", bookCommentMapper.countByUserId(userId));
        return data;
    }

    @Override
    @Transactional
    public void updateContent(Long id, Long userId, String content) {
        BookComment exist = requireOwned(id, userId);
        bookCommentMapper.updateContent(exist.getId(), normalizeContent(content));
        // 改完回待审，评论数跟着变
        refreshCommentCount(exist.getBookId());
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        remove(id, userId);
    }

    @Override
    public Map<String, Object> pageAll(String keyword, Integer auditStatus, Integer pageNum, Integer pageSize) {
        if (auditStatus != null && !ContentAuditStatus.isValid(auditStatus)) {
            throw new BusinessException("审核状态不对");
        }
        int num = PageUtils.pageNum(pageNum);
        int size = PageUtils.pageSize(pageSize, 10);
        String word = keyword == null ? null : keyword.trim();
        Map<String, Object> data = new HashMap<>();
        data.put("list", bookCommentMapper.selectPage(word, auditStatus, PageUtils.offset(num, size), size));
        data.put("total", bookCommentMapper.countPage(word, auditStatus));
        return data;
    }

    @Override
    @Transactional
    public void adminDelete(Long id) {
        remove(id, null);
    }

    @Override
    @Transactional
    public void pass(Long id, Long adminId) {
        BookComment exist = requirePending(id);
        requireParentPassed(exist);
        bookCommentMapper.updateAuditStatus(id, ContentAuditStatus.PASSED.getCode(), null);
        refreshCommentCount(exist.getBookId());
        log.info("评论审核通过 commentId={} adminId={}", id, adminId);
    }

    @Override
    @Transactional
    public void reject(Long id, String remark, Long adminId) {
        String reason = requireRemark(remark);
        BookComment exist = requirePending(id);
        bookCommentMapper.updateAuditStatus(id, ContentAuditStatus.REJECTED.getCode(), reason);
        refreshCommentCount(exist.getBookId());
        log.info("评论驳回 commentId={} adminId={} reason={}", id, adminId, reason);
    }

    /** 只处理待审记录，已审过的再次提交说明列表未刷新 */
    private BookComment requirePending(Long id) {
        BookComment exist = bookCommentMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("评论不存在");
        }
        if (exist.getAuditStatus() == null || exist.getAuditStatus() != ContentAuditStatus.PENDING.getCode()) {
            throw new BusinessException("这条评论已经审过了");
        }
        return exist;
    }

    /** 父评论没通过的话，这条回复通过了也挂不上，读者看不到 */
    private void requireParentPassed(BookComment comment) {
        if (comment.getParentId() == null || comment.getParentId() == ROOT_ID) {
            return;
        }
        BookComment parent = bookCommentMapper.selectById(comment.getParentId());
        if (parent == null || !ContentAuditStatus.isVisible(parent.getAuditStatus())) {
            throw new BusinessException("它回复的那条评论还没通过，先审那条");
        }
    }

    private String requireRemark(String remark) {
        if (remark == null || remark.trim().isEmpty()) {
            throw new BusinessException("驳回要写原因");
        }
        return remark.trim();
    }

    /** userId 为空表示管理员删，一级评论的回复一并清掉 */
    private void remove(Long id, Long userId) {
        BookComment exist = userId == null
                ? bookCommentMapper.selectById(id)
                : requireOwned(id, userId);
        if (exist == null) {
            throw new BusinessException("评论不存在");
        }
        bookCommentMapper.deleteByParentId(exist.getId());
        bookCommentMapper.deleteById(exist.getId());
        refreshCommentCount(exist.getBookId());
    }

    /** 整表重算，不做增量加减；评论数变了详情缓存也要清 */
    private void refreshCommentCount(Long bookId) {
        bookInfoMapper.syncCommentCount(bookId);
        Cache cache = cacheManager.getCache("book");
        if (cache != null) {
            cache.evict(bookId);
        }
    }

    /** 只有本人能改能删，否则拿到 id 就能改别人的话 */
    private BookComment requireOwned(Long id, Long userId) {
        BookComment exist = bookCommentMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("评论不存在");
        }
        if (!exist.getUserId().equals(userId)) {
            throw new BusinessException("只能修改或删除自己的评论");
        }
        return exist;
    }

    private String normalizeContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException("评论内容不能为空");
        }
        String text = content.trim();
        if (text.length() > MAX_CONTENT) {
            throw new BusinessException("评论内容不能超过 " + MAX_CONTENT + " 字");
        }
        return text;
    }

}
