package com.inkisle.service;

import com.inkisle.common.BusinessException;
import com.inkisle.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 作品统计字段的回归测试
 * <p>
 * 阅读次数、总字数、评论数、评分四项都被页面和榜单直接读取，
 * 一旦某一处漏了更新，展示的数字就会长期停住，且不会报错
 * 这里按「产生数据 → 读 book_info 上的列」的方式逐项验证
 */
@Transactional
class BookStatsTest extends BaseIntegrationTest {

    @Autowired
    private BookService bookService;

    @Autowired
    private BookChapterService bookChapterService;

    @Autowired
    private BookCommentService bookCommentService;

    @Autowired
    private BookRatingService bookRatingService;

    @Test
    @DisplayName("打开一章正文后阅读次数加一，作者预览不计数")
    void readingIncreasesVisitCount() {
        long authorId = insertAuthor("stat-visit");
        long bookId = insertBook(authorId, 0, 2);
        long chapterId = insertChapter(bookId, 1);
        insertContent(chapterId, "正文内容");

        bookChapterService.read(chapterId);
        bookChapterService.read(chapterId);
        // 作者预览的是本人稿件，不该被算成一次阅读
        bookChapterService.readForAuthor(chapterId, authorId);

        assertThat(visitCountOf(bookId)).isEqualTo(2);
    }

    @Test
    @DisplayName("保存章节后回填章节字数，作品总字数为各章之和")
    void savingChapterRefreshesWordCount() {
        long authorId = insertAuthor("stat-word");
        long bookId = insertBook(authorId, 0);

        com.inkisle.entity.BookChapter first = new com.inkisle.entity.BookChapter();
        first.setBookId(bookId);
        first.setChapterName("第一章");
        bookChapterService.saveChapter(first, "一二三四五", authorId);

        com.inkisle.entity.BookChapter second = new com.inkisle.entity.BookChapter();
        second.setBookId(bookId);
        second.setChapterName("第二章");
        bookChapterService.saveChapter(second, "六七八", authorId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT word_count FROM book_chapter WHERE id = ?", Integer.class, first.getId()))
                .isEqualTo(5);
        assertThat(wordCountOf(bookId)).isEqualTo(8);
    }

    @Test
    @DisplayName("删除章节后作品总字数同步减少")
    void deletingChapterDecreasesWordCount() {
        long authorId = insertAuthor("stat-word-del");
        long bookId = insertBook(authorId, 0);

        com.inkisle.entity.BookChapter chapter = new com.inkisle.entity.BookChapter();
        chapter.setBookId(bookId);
        chapter.setChapterName("第一章");
        bookChapterService.saveChapter(chapter, "一二三四", authorId);
        assertThat(wordCountOf(bookId)).isEqualTo(4);

        bookChapterService.deleteChapter(chapter.getId(), authorId);

        assertThat(wordCountOf(bookId)).isZero();
    }

    @Test
    @DisplayName("评论审核通过后才计入评论数，删除后回落")
    void commentCountFollowsComments() {
        long authorId = insertAuthor("stat-comment");
        long bookId = insertBook(authorId, 0, 2);

        com.inkisle.entity.BookComment comment = new com.inkisle.entity.BookComment();
        comment.setBookId(bookId);
        comment.setUserId(1L);
        comment.setContent("写得好");
        bookCommentService.add(comment);

        // 新评论待审，读者看不到，列表上的数字也不该先涨上去
        assertThat(commentCountOf(bookId)).isZero();

        bookCommentService.pass(comment.getId(), 1L);
        assertThat(commentCountOf(bookId)).isEqualTo(1);

        bookCommentService.delete(comment.getId(), 1L);

        assertThat(commentCountOf(bookId)).isZero();
    }

    @Test
    @DisplayName("同一读者重复评分只保留一条，平均分与人数同步更新")
    void ratingAggregatesPerReader() {
        long authorId = insertAuthor("stat-rating");
        long bookId = insertBook(authorId, 0, 2);
        long firstUser = insertUser("rating-a", 0);
        long secondUser = insertUser("rating-b", 0);

        bookRatingService.rate(bookId, firstUser, 5);

        com.inkisle.entity.BookInfo book = bookService.getById(bookId);
        assertThat(book.getScore()).isEqualByComparingTo("5.0");
        assertThat(book.getRatingCount()).isEqualTo(1);

        // 同一个人改分，人数不该增加
        bookRatingService.rate(bookId, firstUser, 3);
        bookRatingService.rate(bookId, secondUser, 4);

        com.inkisle.entity.BookInfo updated = bookService.getById(bookId);
        assertThat(updated.getScore()).isEqualByComparingTo("3.5");
        assertThat(updated.getRatingCount()).isEqualTo(2);
        assertThat(countBy("book_rating", "book_id", bookId)).isEqualTo(2);
    }

    @Test
    @DisplayName("未上架的作品不能评分")
    void cannotRateUnpublishedBook() {
        long authorId = insertAuthor("stat-rating-draft");
        long bookId = insertBook(authorId, 0, 0);

        assertThatThrownBy(() -> bookRatingService.rate(bookId, 1L, 5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未上架");
    }

    @Test
    @DisplayName("删除作品时评分一并清理")
    void deleteRemovesRatings() {
        long authorId = insertAuthor("stat-rating-del");
        long bookId = insertBook(authorId, 0, 2);
        bookRatingService.rate(bookId, 1L, 4);

        bookService.delete(bookId, authorId);

        assertThat(countBy("book_rating", "book_id", bookId)).isZero();
    }

    private int visitCountOf(long bookId) {
        return jdbcTemplate.queryForObject("SELECT visit_count FROM book_info WHERE id = ?", Integer.class, bookId);
    }

    private int wordCountOf(long bookId) {
        return jdbcTemplate.queryForObject("SELECT word_count FROM book_info WHERE id = ?", Integer.class, bookId);
    }

    private int commentCountOf(long bookId) {
        return jdbcTemplate.queryForObject("SELECT comment_count FROM book_info WHERE id = ?", Integer.class, bookId);
    }

}
