package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书籍信息
 */
@Data
public class BookInfo {

    private Long id;

    /** 书名 */
    private String bookName;

    /** 作者名 */
    private String authorName;

    /** 分类ID */
    private Long categoryId;

    /** 简介 */
    private String bookDesc;

    /** 封面地址 */
    private String coverUrl;

    /** 状态：0 连载 1 完结 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
