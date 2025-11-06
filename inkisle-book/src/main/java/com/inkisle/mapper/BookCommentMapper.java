package com.inkisle.mapper;

import com.inkisle.entity.BookComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookCommentMapper {

    /** 一级评论：只取最新的一页，避免热书一次把全书评论拉回来 */
    List<BookComment> selectRootsByBookId(@Param("bookId") Long bookId,
                                          @Param("viewerId") Long viewerId,
                                          @Param("limit") int limit);

    /** 当前页一级评论的回复，一次 IN 查完 */
    List<BookComment> selectRepliesByParentIds(@Param("parentIds") List<Long> parentIds,
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

    /**
     * 审核状态唯一写入口，带 status=0 前置条件：
     * 两个管理员同时点通过/驳回时，后到的那次影响 0 行，不会互相覆盖
     */
    int updateAuditStatusIfPending(@Param("id") Long id,
                                   @Param("auditStatus") Integer auditStatus,
                                   @Param("rejectReason") String rejectReason);

    int deleteById(@Param("id") Long id);

    /** 一级评论删掉后回复没有挂载点，要一并清 */
    int deleteByParentId(@Param("parentId") Long parentId);

    int deleteByBookId(@Param("bookId") Long bookId);

}
