package com.inkisle.service;

import com.inkisle.entity.BookComment;

import java.util.List;

public interface BookCommentService {

    /**
     * 评论列表，一级评论下挂载其回复
     */
    List<BookComment> listByBookId(Long bookId);

    void add(BookComment comment);

}
