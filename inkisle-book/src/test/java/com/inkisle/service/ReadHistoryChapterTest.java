package com.inkisle.service;

import com.inkisle.common.BusinessException;
import com.inkisle.entity.BookChapter;
import com.inkisle.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 阅读进度上报前的章节校验
 * 请求体里的 chapterId、chapterNum 都由客户端给，不校验就能给任意作品编一个章节，
 * 顺手把热度榜刷起来，接上积分后还能顺带刷分
 */
@Transactional
class ReadHistoryChapterTest extends BaseIntegrationTest {

    @Autowired
    private BookChapterService bookChapterService;

    @Test
    @DisplayName("章节不属于这本书时拒掉，不能拿 A 书的章节给 B 书加热度")
    void rejectsChapterFromAnotherBook() {
        long authorId = insertAuthor("chapter-owner");
        long firstBook = insertBook(authorId, 0, 2);
        long secondBook = insertBook(authorId, 0, 2);
        long chapterOfFirst = insertChapter(firstBook, 1);

        assertThatThrownBy(() -> bookChapterService.getVisibleChapter(secondBook, chapterOfFirst))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于");
    }

    @Test
    @DisplayName("待审章节不能上报进度")
    void rejectsUnapprovedChapter() {
        long authorId = insertAuthor("chapter-pending");
        long bookId = insertBook(authorId, 0, 2);
        long chapterId = insertChapter(bookId, 1);
        jdbcTemplate.update("UPDATE book_chapter SET audit_status = 0 WHERE id = ?", chapterId);

        assertThatThrownBy(() -> bookChapterService.getVisibleChapter(bookId, chapterId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审核");
    }

    @Test
    @DisplayName("章节号以库里为准，不信客户端传来的值")
    void returnsChapterNumFromDatabase() {
        long authorId = insertAuthor("chapter-ok");
        long bookId = insertBook(authorId, 0, 2);
        long chapterId = insertChapter(bookId, 7);

        BookChapter chapter = bookChapterService.getVisibleChapter(bookId, chapterId);

        assertThat(chapter.getId()).isEqualTo(chapterId);
        assertThat(chapter.getChapterNum()).isEqualTo(7);
    }

    @Test
    @DisplayName("缺参数直接拒掉，不要落到 SQL 上")
    void rejectsNullArguments() {
        assertThatThrownBy(() -> bookChapterService.getVisibleChapter(null, 1L))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> bookChapterService.getVisibleChapter(1L, null))
                .isInstanceOf(BusinessException.class);
    }

}
