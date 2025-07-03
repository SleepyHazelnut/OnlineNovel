package com.inkisle.mapper;

import com.inkisle.entity.BookComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookCommentMapper {

    /**
     * 某本书的评论，按时间正序。只返回审核通过的；
     * viewerId 非空时额外带上该读者自己的评论（待审也能看到自己刚发的那条）
     * 昵称由 LEFT JOIN user_info 取得，用户注销后评论仍需保留展示
     */
    List<BookComment> selectByBookId(@Param("bookId") Long bookId,
                                     @Param("viewerId") Long viewerId);

    /**
     * 单条评论，用于删除前的归属校验
     * 不联表，只取评论自身字段
     */
    BookComment selectById(@Param("id") Long id);

    /**
     * 某位读者的评论，按时间倒序，联表带出书名与昵称
     */
    List<BookComment> selectByUserId(@Param("userId") Long userId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    int countByUserId(@Param("userId") Long userId);

    /**
     * 管理端评论列表，按时间倒序
     * keyword 为空时不过滤，非空时匹配评论内容、书名、昵称三者之一；
     * auditStatus 为空表示不按审核状态筛
     */
    List<BookComment> selectPage(@Param("keyword") String keyword,
                                 @Param("auditStatus") Integer auditStatus,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    int countPage(@Param("keyword") String keyword, @Param("auditStatus") Integer auditStatus);

    int insert(BookComment comment);

    /**
     * 仅更新内容。编辑不改变评论归属与层级，故不开放其他字段
     * 修改后审核状态回到待审
     */
    int updateContent(@Param("id") Long id, @Param("content") String content);

    /**
     * 审核状态唯一写入口，通过时 rejectReason 传 null
     */
    int updateAuditStatus(@Param("id") Long id,
                          @Param("auditStatus") Integer auditStatus,
                          @Param("rejectReason") String rejectReason);

    /**
     * 删除单条评论
     */
    int deleteById(@Param("id") Long id);

    /**
     * 删除某条评论下的全部回复。一级评论被删时回复失去挂载点，需一并清理
     */
    int deleteByParentId(@Param("parentId") Long parentId);

    /**
     * 删除某本书的全部评论。作品被删时评论随之清理
     */
    int deleteByBookId(@Param("bookId") Long bookId);

}
