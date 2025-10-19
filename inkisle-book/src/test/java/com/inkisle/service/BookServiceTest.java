package com.inkisle.service;

import com.inkisle.common.BusinessException;
import com.inkisle.dto.BookSaveDTO;
import com.inkisle.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 作品服务的回归测试
 * 都是之前真踩过的：删作品只删主表会剩一堆孤儿数据；
 * 编辑时 mapper 无条件更新 status，连载状态被写成 null
 */
@Transactional
class BookServiceTest extends BaseIntegrationTest {

    @Autowired
    private BookService bookService;

    /**
     * 这个用例必须真提交：清理审核记录改到了事务提交之后，
     * 外层再套一个回滚的事务，afterCommit 永远不会触发，验证不到
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
        // 正文是按章节 id 关联的，章节删了再也定位不到，所以断言得用章节 id
        assertThat(countBy("book_content", "chapter_id", chapterId)).isZero();
        assertThat(countBy("book_comment", "book_id", bookId)).isZero();
        assertThat(countBy("user_bookshelf", "book_id", bookId)).isZero();
        assertThat(countBy("user_read_history", "book_id", bookId)).isZero();

        // 审核记录归审核服务管，这边删不到，只能确认清理请求发出去了
        Mockito.verify(auditClient).deleteByBookId(bookId);

        // 本用例不滚事务，自己收拾留下的审核记录
        jdbcTemplate.update("DELETE FROM book_audit_record WHERE book_id = ?", bookId);
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

    @Test
    @DisplayName("未启用 ES 时搜索走 MySQL，未上架的书搜不到")
    void searchFallsBackToMysqlWhenEsDisabled() {
        long authorId = insertAuthor("book-search");
        long visibleId = insertBook(authorId, 0, 2);
        jdbcTemplate.update("UPDATE book_info SET book_name = ? WHERE id = ?", "沧海拾遗", visibleId);
        long draftId = insertBook(authorId, 0, 0);
        jdbcTemplate.update("UPDATE book_info SET book_name = ? WHERE id = ?", "沧海未上架", draftId);

        // 用单字：ngram 最小切分是 2，单字走 LIKE 那段兜底，
        // 顺带绕开「全文索引看不到本事务未提交的行」这个 InnoDB FTS 的特性
        List<?> hits = listOf(bookService.search(1, 10, "沧"));

        // 上架的那本命中，草稿那本因为 audit_status 不为 2 被过滤掉
        assertThat(hits).hasSize(1);
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
