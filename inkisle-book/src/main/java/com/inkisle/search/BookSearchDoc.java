package com.inkisle.search;

import com.inkisle.entity.BookInfo;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 书籍在 ES 里的文档
 * 正文不放，太长，搜的是书不是章节
 * createIndex 关掉，索引交给 BookSearchIndexer 创建，避免启动时连接 ES
 */
@Document(indexName = BookSearchDoc.INDEX, createIndex = false)
public class BookSearchDoc {

    public static final String INDEX = "inkisle_book";

    @Id
    private Long id;

    /** 书名权重最高，权重在查询里给 */
    @Field(type = FieldType.Text)
    private String bookName;

    @Field(type = FieldType.Text)
    private String authorName;

    /** 简介也参与匹配，权重最低 */
    @Field(type = FieldType.Text)
    private String bookDesc;

    @Field(type = FieldType.Long)
    private Long categoryId;

    /** 封面对搜索无用，存下来只为回填页面，不建索引 */
    @Field(type = FieldType.Keyword, index = false)
    private String coverUrl;

    /** 0 连载 1 完结 */
    @Field(type = FieldType.Integer)
    private Integer status;

    /** 冗余一份可见性，搜索时直接按它过滤，不必回表 */
    @Field(type = FieldType.Integer)
    private Integer auditStatus;

    @Field(type = FieldType.Integer)
    private Integer visitCount;

    @Field(type = FieldType.Integer)
    private Integer wordCount;

    @Field(type = FieldType.Integer)
    private Integer commentCount;

    /** ES 没有 BigDecimal，用 double 存，回填时再转回去 */
    @Field(type = FieldType.Double)
    private Double score;

    public static BookSearchDoc from(BookInfo book) {
        BookSearchDoc doc = new BookSearchDoc();
        doc.setId(book.getId());
        doc.setBookName(book.getBookName());
        doc.setAuthorName(book.getAuthorName());
        doc.setBookDesc(book.getBookDesc());
        doc.setCategoryId(book.getCategoryId());
        doc.setCoverUrl(book.getCoverUrl());
        doc.setStatus(book.getStatus());
        doc.setAuditStatus(book.getAuditStatus());
        doc.setVisitCount(book.getVisitCount());
        doc.setWordCount(book.getWordCount());
        doc.setCommentCount(book.getCommentCount());
        doc.setScore(book.getScore() == null ? null : book.getScore().doubleValue());
        return doc;
    }

    /** 命中的文档转回页面要的结构，前端两套搜索结果才能共用一套渲染 */
    public BookInfo toBookInfo() {
        BookInfo book = new BookInfo();
        book.setId(id);
        book.setBookName(bookName);
        book.setAuthorName(authorName);
        book.setBookDesc(bookDesc);
        book.setCategoryId(categoryId);
        book.setCoverUrl(coverUrl);
        book.setStatus(status);
        book.setAuditStatus(auditStatus);
        book.setVisitCount(visitCount);
        book.setWordCount(wordCount);
        book.setCommentCount(commentCount);
        book.setScore(score == null ? null : java.math.BigDecimal.valueOf(score));
        return book;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getBookDesc() {
        return bookDesc;
    }

    public void setBookDesc(String bookDesc) {
        this.bookDesc = bookDesc;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getAuditStatus() {
        return auditStatus;
    }

    public void setAuditStatus(Integer auditStatus) {
        this.auditStatus = auditStatus;
    }

    public Integer getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(Integer visitCount) {
        this.visitCount = visitCount;
    }

    public Integer getWordCount() {
        return wordCount;
    }

    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }

    public Integer getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(Integer commentCount) {
        this.commentCount = commentCount;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

}
