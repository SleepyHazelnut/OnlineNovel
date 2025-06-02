package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书籍章节
 */
@Data
public class BookChapter {

    private Long id;

    /** 所属书籍ID */
    private Long bookId;

    /** 章节名 */
    private String chapterName;

    /** 章节序号，从 1 开始 */
    private Integer chapterNum;

    /** 章节正文 */
    private String content;

    private LocalDateTime createTime;

}
