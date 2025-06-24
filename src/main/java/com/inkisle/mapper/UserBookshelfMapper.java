package com.inkisle.mapper;

import com.inkisle.entity.UserBookshelf;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserBookshelfMapper {

    /**
     * 统计是否已收藏，Service 层据此拦截重复收藏
     */
    int countByUserAndBook(@Param("userId") Long userId, @Param("bookId") Long bookId);

    int insert(UserBookshelf bookshelf);

    int deleteByUserAndBook(@Param("userId") Long userId, @Param("bookId") Long bookId);

    /**
     * 删除某本书的全部收藏。作品被删后书架里不该留下一本打不开的书
     */
    int deleteByBookId(@Param("bookId") Long bookId);

    /**
     * 用户书架列表。收藏表仅存 book_id，书名与封面由 LEFT JOIN book_info 取得
     */
    List<UserBookshelf> selectByUserId(@Param("userId") Long userId);

}
