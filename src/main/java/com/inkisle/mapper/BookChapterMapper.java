package com.inkisle.mapper;

import com.inkisle.entity.BookChapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookChapterMapper {

    /**
     * 某本书的全部章节，按 chapter_num 升序
     */
    List<BookChapter> selectListByBookId(@Param("bookId") Long bookId);

    BookChapter selectById(@Param("id") Long id);

    /**
     * 上一章ID，没有则返回 null
     */
    Long selectPrevId(@Param("bookId") Long bookId, @Param("chapterNum") Integer chapterNum);

    /**
     * 下一章ID，没有则返回 null
     */
    Long selectNextId(@Param("bookId") Long bookId, @Param("chapterNum") Integer chapterNum);

}
