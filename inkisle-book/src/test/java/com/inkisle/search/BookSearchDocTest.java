package com.inkisle.search;

import com.inkisle.entity.BookInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 索引文档和实体之间的转换
 * 看着是两个纯函数没什么可测的，但少转一个字段，搜索结果里就少一块内容，
 * 前端书卡片的评分、字数、封面全靠这几个，所以单独拎出来
 */
class BookSearchDocTest {

    @Test
    @DisplayName("实体转文档再转回来，搜索页要用的字段不丢")
    void roundTripKeepsDisplayFields() {
        BookInfo book = new BookInfo();
        book.setId(7L);
        book.setBookName("沧海拾遗");
        book.setAuthorName("白墨");
        book.setBookDesc("简介");
        book.setCategoryId(3L);
        book.setCoverUrl("/upload/a.png");
        book.setStatus(1);
        book.setAuditStatus(2);
        book.setVisitCount(120);
        book.setWordCount(3400);
        book.setCommentCount(9);
        book.setScore(new BigDecimal("4.5"));

        BookInfo back = BookSearchDoc.from(book).toBookInfo();

        assertThat(back.getId()).isEqualTo(7L);
        assertThat(back.getBookName()).isEqualTo("沧海拾遗");
        assertThat(back.getAuthorName()).isEqualTo("白墨");
        assertThat(back.getCoverUrl()).isEqualTo("/upload/a.png");
        assertThat(back.getStatus()).isEqualTo(1);
        assertThat(back.getAuditStatus()).isEqualTo(2);
        assertThat(back.getVisitCount()).isEqualTo(120);
        assertThat(back.getWordCount()).isEqualTo(3400);
        assertThat(back.getScore()).isEqualByComparingTo("4.5");
    }

    @Test
    @DisplayName("没人评分的书，分数为 null 也不会炸")
    void nullScoreIsTolerated() {
        BookInfo book = new BookInfo();
        book.setId(8L);
        book.setScore(null);

        assertThat(BookSearchDoc.from(book).toBookInfo().getScore()).isNull();
    }

}
