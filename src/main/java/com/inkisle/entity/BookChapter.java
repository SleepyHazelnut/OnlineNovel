package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书籍章节
 * 只存目录信息，正文单独存放于 BookContent
 * 正文与章节合并建表时，查询章节列表会一并取出大体积正文，故拆分
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

    /** 本章字数，由正文长度统计 */
    private Integer wordCount;

    private LocalDateTime createTime;

}
