package com.inkisle.mapper;

import com.inkisle.entity.BookChapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookChapterMapper {

    /** 目录用，不带正文 */
    List<BookChapter> selectListByBookId(@Param("bookId") Long bookId);

    /** 作者端与审核预览用，含待审、驳回的章节 */
    List<BookChapter> selectAllByBookId(@Param("bookId") Long bookId);

    BookChapter selectById(@Param("id") Long id);

    Long selectPrevId(@Param("bookId") Long bookId, @Param("chapterNum") Integer chapterNum);

    Long selectNextId(@Param("bookId") Long bookId, @Param("chapterNum") Integer chapterNum);

    Integer selectMaxChapterNum(@Param("bookId") Long bookId);

    int insert(BookChapter chapter);

    int updateById(BookChapter chapter);

    /** 通过时 rejectReason 传 null，清掉上一次的驳回原因 */
    int updateAuditStatus(@Param("id") Long id,
                          @Param("auditStatus") Integer auditStatus,
                          @Param("rejectReason") String rejectReason);

    int passAllByBookId(@Param("bookId") Long bookId);

    List<BookChapter> selectPageByStatus(@Param("auditStatus") Integer auditStatus,
                                         @Param("offset") int offset,
                                         @Param("pageSize") int pageSize);

    int countByStatus(@Param("auditStatus") Integer auditStatus);

    int updateWordCount(@Param("id") Long id, @Param("wordCount") int wordCount);

    int selectTotalWordCount(@Param("bookId") Long bookId);

    int deleteById(@Param("id") Long id);

    /** 正文挂在章节上，调之前得先把 book_content 清了 */
    int deleteByBookId(@Param("bookId") Long bookId);

}
