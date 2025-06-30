package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.common.PageUtils;
import com.inkisle.entity.BookComment;
import com.inkisle.mapper.BookCommentMapper;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.service.BookCommentService;
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
 * 评论
 * 与分类相同，为两级结构：一级评论 `parent_id` 为 0，回复挂载于其下
 */
@Service
public class BookCommentServiceImpl implements BookCommentService {

    /** 一级评论的 parent_id 约定为 0 */
    private static final long ROOT_ID = 0L;

    /** 与 `book_comment.content` 的 varchar(500) 保持一致，超长由数据库报错不如提前拦下 */
    private static final int MAX_CONTENT = 500;

    @Autowired
    private BookCommentMapper bookCommentMapper;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Autowired
    private CacheManager cacheManager;

    @Override
    public List<BookComment> listByBookId(Long bookId) {
        List<BookComment> all = bookCommentMapper.selectByBookId(bookId);

        List<BookComment> roots = new ArrayList<>();
        for (BookComment comment : all) {
            if (comment.getParentId() == ROOT_ID) {
                roots.add(comment);
            }
        }

        // 评论一次全部查出并在内存中组装，避免为每条回复单独查询父评论
        for (BookComment root : roots) {
            List<BookComment> children = new ArrayList<>();
            for (BookComment comment : all) {
                if (comment.getParentId().equals(root.getId())) {
                    children.add(comment);
                }
            }
            root.setChildren(children);
        }
        return roots;
    }

    @Override
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
            // 只保留两级。对回复再回复时挂到该回复所属的一级评论下，
            // 否则父节点是一条回复，列表组装时它既不是根也挂不到任何根下，评论会凭空消失
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
    public void updateContent(Long id, Long userId, String content) {
        BookComment exist = requireOwned(id, userId);
        bookCommentMapper.updateContent(exist.getId(), normalizeContent(content));
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        remove(id, userId);
    }

    @Override
    public Map<String, Object> pageAll(String keyword, Integer pageNum, Integer pageSize) {
        int num = PageUtils.pageNum(pageNum);
        int size = PageUtils.pageSize(pageSize, 10);
        String word = keyword == null ? null : keyword.trim();
        Map<String, Object> data = new HashMap<>();
        data.put("list", bookCommentMapper.selectPage(word, PageUtils.offset(num, size), size));
        data.put("total", bookCommentMapper.countPage(word));
        return data;
    }

    @Override
    @Transactional
    public void adminDelete(Long id) {
        remove(id, null);
    }

    /**
     * 删除评论。userId 为空表示管理员操作，不做归属校验
     * 一级评论被删时其回复失去父节点，需一并清理，否则会变成查不出来的脏数据
     */
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

    /**
     * 评论数是作品列表与详情上展示的字段，增删后重算并清掉详情缓存
     * 不增量加减的原因见 BookInfoMapper.syncCommentCount 上方注释
     */
    private void refreshCommentCount(Long bookId) {
        bookInfoMapper.syncCommentCount(bookId);
        Cache cache = cacheManager.getCache("book");
        if (cache != null) {
            cache.evict(bookId);
        }
    }

    /**
     * 取出评论并确认归属。评论是用户自己发的内容，只有本人能改能删，
     * 否则任何人拿到 id 就能改动别人的发言
     */
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
