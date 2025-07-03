package com.inkisle.mapper;

import com.inkisle.entity.BookComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookCommentMapper {

    /** 只取已通过的；viewerId 非空时额外带上他自己的待审评论 */
    List<BookComment> selectByBookId(@Param("bookId") Long bookId,
                                     @Param("viewerId") Long viewerId);

    BookComment selectById(@Param("id") Long id);

    List<BookComment> selectByUserId(@Param("userId") Long userId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    int countByUserId(@Param("userId") Long userId);

    List<BookComment> selectPage(@Param("keyword") String keyword,
                                 @Param("auditStatus") Integer auditStatus,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    int countPage(@Param("keyword") String keyword, @Param("auditStatus") Integer auditStatus);

    int insert(BookComment comment);

    int updateContent(@Param("id") Long id, @Param("content") String content);

    int updateAuditStatus(@Param("id") Long id,
                          @Param("auditStatus") Integer auditStatus,
                          @Param("rejectReason") String rejectReason);

    int deleteById(@Param("id") Long id);

    /** 一级评论删掉后回复没有挂载点，要一并清 */
    int deleteByParentId(@Param("parentId") Long parentId);

    int deleteByBookId(@Param("bookId") Long bookId);

}
