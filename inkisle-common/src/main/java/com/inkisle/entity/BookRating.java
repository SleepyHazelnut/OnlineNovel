package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书籍评分
 * 一个人对一本书只有一条评分，再评就覆盖，(book_id, user_id) 上有唯一键
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
