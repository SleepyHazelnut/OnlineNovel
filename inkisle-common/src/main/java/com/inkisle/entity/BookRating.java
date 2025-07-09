package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书籍评分
 * 一位读者对同一本书只保留一条评分，重复评分覆盖旧值，唯一键为 (book_id, user_id)
 */
@Data
public class BookRating {

    private Long id;

    private Long bookId;

    private Long userId;

    /** 评分 1-5 星 */
    private Integer score;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
