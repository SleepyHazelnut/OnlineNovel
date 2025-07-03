package com.inkisle.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 书籍信息，目录在 BookChapter，正文在 BookContent */
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

    /**
     * 上架状态，取值见 BookAuditStatus
     * 只有「已通过」对读者可见，读者端所有查询都以此列为准
     */
    private Integer auditStatus;

    /** 最近一次审核意见：驳回原因或下架原因，通过时清空 */
    private String auditRemark;

    /** 阅读次数，每打开一章累加一次 */
    private Integer visitCount;

    /** 总字数，由章节正文字数汇总 */
    private Integer wordCount;

    /** 评论数，只统计审核通过的评论 */
    private Integer commentCount;

    /** 平均评分 0-5，无人评分时为 0。decimal 取出后用 BigDecimal，避免 4.5 变成 4.4999 */
    private BigDecimal score;

    /** 评分人数 */
    private Integer ratingCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
