package com.inkisle.service;

import com.inkisle.entity.BookChapter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface BookChapterService {

    List<BookChapter> listByBookId(Long bookId);

    /** 作者端要带出草稿，所以先校验这本书是不是他的 */
    List<BookChapter> listByBookIdForAuthor(Long bookId, Long authorId);

    Map<String, Object> read(Long id);

    /** 作者预览自己的稿子，只校验归属，不看审核状态 */
    Map<String, Object> readForAuthor(Long id, Long authorId);

    void saveChapter(BookChapter chapter, String content, Long authorId);

    void deleteChapter(Long chapterId, Long authorId);

    int importChapters(Long bookId, MultipartFile file, Long authorId);

}
