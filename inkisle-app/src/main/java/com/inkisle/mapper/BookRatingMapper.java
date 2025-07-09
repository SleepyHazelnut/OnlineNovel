package com.inkisle.mapper;

import com.inkisle.entity.BookRating;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface BookRatingMapper {

    /**
     * 某位读者对某本书的评分，未评过返回 null
     */
    BookRating selectByBookAndUser(@Param("bookId") Long bookId, @Param("userId") Long userId);

    /**
     * 写入评分。同一读者重复评分时覆盖旧值，
     * 由唯一键 uk_book_user 触发更新，避免先查后插的并发重复
     */
    int upsert(BookRating rating);

    /**
     * 聚合某本书的平均分与人数，返回 key 为 avgScore、ratingCount
     * 无人评分时 AVG 为 null，COUNT 为 0，Service 侧按 0 处理
     */
    Map<String, Object> selectSummary(@Param("bookId") Long bookId);

    /**
     * 删除某本书的全部评分，作品被删时一并清理
     */
    int deleteByBookId(@Param("bookId") Long bookId);

}
