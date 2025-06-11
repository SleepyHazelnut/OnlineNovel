package com.inkisle.mapper;

import com.inkisle.entity.BookChapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookChapterMapper {

    /**
     * 某本书的全部章节，按 chapter_num 升序
     * 结果不含正文，避免目录查询连带取出整本书内容
     */
    List<BookChapter> selectListByBookId(@Param("bookId") Long bookId);

    BookChapter selectById(@Param("id") Long id);

    /**
     * 上一章 ID。返回类型为 Long 而非 long，无上一章时返回 null，
     * 前端据此决定「上一章」按钮是否置灰
     */
    Long selectPrevId(@Param("bookId") Long bookId, @Param("chapterNum") Integer chapterNum);

    /**
     * 下一章 ID，同上；最后一章时返回 null
     */
    Long selectNextId(@Param("bookId") Long bookId, @Param("chapterNum") Integer chapterNum);

    /**
     * 当前最大章节号，新章节接在后面
     */
    Integer selectMaxChapterNum(@Param("bookId") Long bookId);

    int insert(BookChapter chapter);

    int updateById(BookChapter chapter);

    int deleteById(@Param("id") Long id);

}
