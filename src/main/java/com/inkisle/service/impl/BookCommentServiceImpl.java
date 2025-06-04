package com.inkisle.service.impl;

import com.inkisle.entity.BookComment;
import com.inkisle.mapper.BookCommentMapper;
import com.inkisle.service.BookCommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 评论
 * 与分类相同，为两级结构：一级评论 `parent_id` 为 0，回复挂载于其下
 */
@Service
public class BookCommentServiceImpl implements BookCommentService {

    /** 一级评论的 parent_id 约定为 0 */
    private static final long ROOT_ID = 0L;

    @Autowired
    private BookCommentMapper bookCommentMapper;

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
        if (comment.getParentId() == null) {
            comment.setParentId(ROOT_ID);
        }
        bookCommentMapper.insert(comment);
    }

}
