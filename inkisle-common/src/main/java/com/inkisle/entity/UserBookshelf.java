package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书架收藏
 * 一个用户一本书只有一条，库里靠 user_id + book_id 的唯一索引拦住
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
