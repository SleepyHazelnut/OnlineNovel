package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书籍信息
 * 书籍基础信息：书名、简介、封面等。目录见 BookChapter，正文见 BookContent
 * 简介单独占一个字段，列表页直接读取，无需加载正文
 */
@Data
public class BookInfo {

    private Long id;

    /** 书名 */
    private String bookName;

    /** 作者名 */
    private String authorName;

    /** 作者ID，读者自己发的书为空 */
    private Long authorId;

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
