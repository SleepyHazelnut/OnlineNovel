package com.inkisle.service;

import com.inkisle.entity.BookChapter;

import java.util.List;
import java.util.Map;

public interface BookChapterService {

    /**
     * 某本书的章节目录
     */
    List<BookChapter> listByBookId(Long bookId);

    /**
     * 阅读某一章，返回章节正文和上下章ID
     */
    Map<String, Object> read(Long id);

}
