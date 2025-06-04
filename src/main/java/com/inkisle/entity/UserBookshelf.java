package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书架收藏
 * 一个用户对一本书只存一条，数据库以 user_id + book_id 唯一索引约束
 */
@Data
public class UserBookshelf {

    private Long id;

    private Long userId;

    private Long bookId;

    private LocalDateTime createTime;

    /** 以下三个字段由联表带出，收藏列表直接展示，省去二次查询书籍 */
    private String bookName;

    private String authorName;

    private String coverUrl;

}
