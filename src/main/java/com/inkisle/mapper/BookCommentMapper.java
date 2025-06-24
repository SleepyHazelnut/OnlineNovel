package com.inkisle.mapper;

import com.inkisle.entity.BookComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookCommentMapper {

    /**
     * 某本书的全部评论，按时间正序。评论表只存 user_id，
     * 昵称由 LEFT JOIN user_info 取得，用户注销后评论仍需保留展示
     */
    List<BookComment> selectByBookId(@Param("bookId") Long bookId);

    int insert(BookComment comment);

    /**
     * 删除某本书的全部评论。作品被删时评论随之清理
     */
    int deleteByBookId(@Param("bookId") Long bookId);

}
