package com.inkisle.service;

import com.inkisle.entity.BookChapter;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 保存章节，章节信息和正文要落两张表
     */
    void saveChapter(BookChapter chapter, String content, Long authorId);

    /**
     * 删除章节，正文一起删
     */
    void deleteChapter(Long chapterId, Long authorId);

    /**
     * 导入整本 TXT，返回这次导进去多少章
     */
    int importChapters(Long bookId, MultipartFile file, Long authorId);

}
