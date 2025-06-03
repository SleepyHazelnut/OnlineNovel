package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阅读记录
 * 一个用户对一本书只保留一条，读到新章节时更新该条的 chapterId，不新增行
 */
@Data
public class UserReadHistory {

    private Long id;

    private Long userId;

    private Long bookId;

    /** 读到哪一章 */
    private Long chapterId;

    private Integer chapterNum;

    private LocalDateTime updateTime;

    /** 书名，列表查询时联表带出来 */
    private String bookName;

}
