package com.inkisle.service;

import com.inkisle.common.BusinessException;
import com.inkisle.dto.BookSaveDTO;
import com.inkisle.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 作品服务的回归测试
 * <p>
 * 覆盖两类曾经出过问题的地方：删除作品只删主表会留下孤儿数据；
 * 编辑作品时 mapper 无条件更新 status，会把连载状态写成 null
 */
@Transactional
class BookServiceTest extends BaseIntegrationTest {

    @Autowired
    private BookService bookService;

    @Test
    @DisplayName("删除作品时清理章节、正文、评论、收藏、进度与审核记录")
    void deleteAlsoRemovesRelatedRows() {
        long authorId = insertAuthor("book-delete");
        long bookId = insertBook(authorId, 0);
        long chapterId = insertChapter(bookId, 1);
        insertContent(chapterId, "正文");
        insertComment(bookId, 1L);
        insertBookshelf(1L, bookId);
        insertHistory(1L, bookId, chapterId);
        insertAuditRecord(bookId, authorId, 1);

        bookService.delete(bookId, authorId);

        assertThat(countBy("book_info", "id", bookId)).isZero();
        assertThat(countBy("book_chapter", "book_id", bookId)).isZero();
        // 正文按章节 id 关联，删完章节就再也定位不到，故断言必须用章节 id
        assertThat(countBy("book_content", "chapter_id", chapterId)).isZero();
        assertThat(countBy("book_comment", "book_id", bookId)).isZero();
        assertThat(countBy("user_bookshelf", "book_id", bookId)).isZero();
        assertThat(countBy("user_read_history", "book_id", bookId)).isZero();

        // 审核记录归审核服务管，这边删不到，只能确认清理请求发出去了
        Mockito.verify(auditClient).deleteByBookId(bookId);
    }

    @Test
    @DisplayName("不能删除他人的作品，数据须保持不变")
    void cannotDeleteAnotherAuthorsBook() {
        long ownerId = insertAuthor("book-owner");
        long otherId = insertAuthor("book-other");
        long bookId = insertBook(ownerId, 0);
        long chapterId = insertChapter(bookId, 1);
        insertContent(chapterId, "正文");

        assertThatThrownBy(() -> bookService.delete(bookId, otherId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能删除自己的作品");

        assertThat(countBy("book_info", "id", bookId)).isEqualTo(1);
        assertThat(countBy("book_chapter", "book_id", bookId)).isEqualTo(1);
        assertThat(countBy("book_content", "chapter_id", chapterId)).isEqualTo(1);
    }

    @Test
    @DisplayName("编辑作品只改展示信息，连载状态要保持原值")
    void updateKeepsPublishStatus() {
        long authorId = insertAuthor("book-edit");
        long bookId = insertBook(authorId, 1);

        BookSaveDTO dto = new BookSaveDTO();
        dto.setId(bookId);
        dto.setBookName("改过的书名");
        dto.setBookDesc("改过的简介");
        dto.setAuthorName("测试作者");

        bookService.save(dto, authorId);

        assertThat(bookNameOf(bookId)).isEqualTo("改过的书名");
        assertThat(statusOf(bookId)).isEqualTo(1);
    }

    @Test
    @DisplayName("每页条数有上限，页码与空关键词都归一化")
    void pageAndSearchParamsAreClamped() {
        long authorId = insertAuthor("book-page");
        for (int i = 0; i < 120; i++) {
            insertBook(authorId, 0, 2);
        }

        // 前端传 10000 也只取 100 条，不会把整张表拖走
        assertThat(listOf(bookService.page(1, 10000, null))).hasSize(100);
        // 页码 0、条数 0 按第一页与默认条数处理，不会算出负的 offset
        assertThat(listOf(bookService.page(0, 0, null))).hasSize(10);

        // 空关键词原本会退化成 LIKE '%%'，等于列全部
        assertThat(listOf(bookService.search(1, 10, "   "))).isEmpty();
        assertThatThrownBy(() -> bookService.search(1, 10, "剑".repeat(65)))
                .isInstanceOf(BusinessException.class);
    }

    @SuppressWarnings("unchecked")
    private List<?> listOf(Map<String, Object> page) {
        return (List<?>) page.get("list");
    }

    private String bookNameOf(long bookId) {
        return jdbcTemplate.queryForObject("SELECT book_name FROM book_info WHERE id = ?", String.class, bookId);
    }

    private Integer statusOf(long bookId) {
        return jdbcTemplate.queryForObject("SELECT status FROM book_info WHERE id = ?", Integer.class, bookId);
    }

}
