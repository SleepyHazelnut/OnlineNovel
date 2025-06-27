package com.inkisle.service;

import com.inkisle.common.BusinessException;
import com.inkisle.dto.BookSaveDTO;
import com.inkisle.entity.BookChapter;
import com.inkisle.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 审核流程的回归测试
 * <p>
 * 这里验证的是「读者能不能看到」这件事本身，而不是某个字段的值：
 * 早先新书一建就对读者可见，审核通过与否只影响审核记录表，
 * 提交审核形同虚设。现在可见性只认 book_info.audit_status
 */
@Transactional
class BookAuditServiceTest extends BaseIntegrationTest {

    /** 上架状态取值与 BookAuditStatus 一致 */
    private static final int DRAFT = 0;
    private static final int PENDING = 1;
    private static final int PASSED = 2;
    private static final int REJECTED = 3;
    private static final int OFFLINE = 4;

    @Autowired
    private BookAuditService bookAuditService;

    @Autowired
    private BookService bookService;

    @Autowired
    private BookChapterService bookChapterService;

    @Autowired
    private UserBookshelfService userBookshelfService;

    @Autowired
    private UserReadHistoryService userReadHistoryService;

    @Test
    @DisplayName("新建的作品是草稿，读者看不到")
    void newBookIsDraftAndInvisible() {
        long authorId = insertAuthor("audit-new");

        BookSaveDTO dto = new BookSaveDTO();
        dto.setBookName("新书");
        dto.setBookDesc("简介");
        dto.setCategoryId(1L);
        bookService.save(dto, authorId);

        long bookId = latestBookId(authorId);
        assertThat(auditStatusOf(bookId)).isEqualTo(DRAFT);
        assertThat(bookService.isVisibleToReader(bookId)).isFalse();
    }

    @Test
    @DisplayName("提交审核进入待审，通过后才对读者可见")
    void submitThenPassMakesBookVisible() {
        long authorId = insertAuthor("audit-pass");
        long bookId = insertBook(authorId, 0);
        insertChapter(bookId, 1);

        bookAuditService.submit(bookId, authorId);
        assertThat(auditStatusOf(bookId)).isEqualTo(PENDING);
        assertThat(bookService.isVisibleToReader(bookId)).isFalse();

        bookAuditService.pass(latestRecordId(bookId), 99L);
        assertThat(auditStatusOf(bookId)).isEqualTo(PASSED);
        assertThat(bookService.isVisibleToReader(bookId)).isTrue();
    }

    @Test
    @DisplayName("驳回后不可见，驳回原因写到作品上供作者查看")
    void rejectMakesInvisibleAndKeepsReason() {
        long authorId = insertAuthor("audit-reject");
        long bookId = insertBook(authorId, 0);
        insertChapter(bookId, 1);
        bookAuditService.submit(bookId, authorId);

        bookAuditService.reject(latestRecordId(bookId), "内容不符合要求", 99L);

        assertThat(auditStatusOf(bookId)).isEqualTo(REJECTED);
        assertThat(auditRemarkOf(bookId)).isEqualTo("内容不符合要求");
        assertThat(bookService.isVisibleToReader(bookId)).isFalse();
    }

    @Test
    @DisplayName("已上架作品改简介会重新送审，改章节正文不会")
    void editingDisplayInfoResubmitsButChapterDoesNot() {
        long authorId = insertAuthor("audit-edit");
        long bookId = insertBook(authorId, 0, PASSED);

        // 更新章节正文：读者看到的内容变了，但目录和简介没变，不必重新审核，
        // 否则作者每更一章书都要下架等审，功能没法用
        BookChapter chapter = new BookChapter();
        chapter.setBookId(bookId);
        chapter.setChapterName("第一章");
        bookChapterService.saveChapter(chapter, "正文", authorId);
        assertThat(auditStatusOf(bookId)).isEqualTo(PASSED);

        BookSaveDTO dto = new BookSaveDTO();
        dto.setId(bookId);
        dto.setBookName("改过的书名");
        dto.setCategoryId(1L);
        bookService.save(dto, authorId);

        assertThat(auditStatusOf(bookId)).isEqualTo(PENDING);
        assertThat(bookService.isVisibleToReader(bookId)).isFalse();
        // 重新送审要留一条待审记录，否则管理端列表里看不到这本书
        assertThat(countBy("book_audit_record", "book_id", bookId)).isEqualTo(1);
    }

    @Test
    @DisplayName("没有实际改动时保存，不应把已上架的书打回待审")
    void savingWithoutChangesKeepsStatus() {
        long authorId = insertAuthor("audit-nochange");
        long bookId = insertBook(authorId, 0, PASSED);
        String name = bookNameOf(bookId);

        BookSaveDTO dto = new BookSaveDTO();
        dto.setId(bookId);
        dto.setBookName(name);
        dto.setAuthorName("测试作者");
        dto.setBookDesc("简介");
        dto.setCategoryId(1L);
        // 编辑页回填时空封面会变成空串，不能因此判定为「改了封面」
        dto.setCoverUrl("");
        bookService.save(dto, authorId);

        assertThat(auditStatusOf(bookId)).isEqualTo(PASSED);
    }

    @Test
    @DisplayName("下架后读者不可见，恢复上架后重新可见")
    void takeDownAndRestore() {
        long authorId = insertAuthor("audit-offline");
        long bookId = insertBook(authorId, 0, PASSED);

        bookAuditService.takeDown(bookId, "收到侵权投诉", 99L);
        assertThat(auditStatusOf(bookId)).isEqualTo(OFFLINE);
        assertThat(auditRemarkOf(bookId)).isEqualTo("收到侵权投诉");
        assertThat(bookService.isVisibleToReader(bookId)).isFalse();

        bookAuditService.restore(bookId, 99L);
        assertThat(auditStatusOf(bookId)).isEqualTo(PASSED);
        assertThat(bookService.isVisibleToReader(bookId)).isTrue();
        // 恢复上架要把下架原因清掉，否则作者一直看到一条过期的说明
        assertThat(auditRemarkOf(bookId)).isNull();
    }

    @Test
    @DisplayName("下架必须写原因，只有已上架的书能下架")
    void takeDownRequiresReasonAndPassedBook() {
        long authorId = insertAuthor("audit-takedown");
        long passedBook = insertBook(authorId, 0, PASSED);
        long draftBook = insertBook(authorId, 0, DRAFT);

        assertThatThrownBy(() -> bookAuditService.takeDown(passedBook, "  ", 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下架要写原因");

        assertThatThrownBy(() -> bookAuditService.takeDown(draftBook, "违规", 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有已上架的作品可以下架");

        assertThat(auditStatusOf(passedBook)).isEqualTo(PASSED);
    }

    @Test
    @DisplayName("没有章节的作品不能提交审核")
    void cannotSubmitBookWithoutChapter() {
        long authorId = insertAuthor("audit-empty");
        long bookId = insertBook(authorId, 0);

        assertThatThrownBy(() -> bookAuditService.submit(bookId, authorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("还没有章节");
        assertThat(auditStatusOf(bookId)).isEqualTo(DRAFT);
    }

    @Test
    @DisplayName("待审期间重复提交会被挡住")
    void cannotSubmitTwice() {
        long authorId = insertAuthor("audit-twice");
        long bookId = insertBook(authorId, 0);
        insertChapter(bookId, 1);
        bookAuditService.submit(bookId, authorId);

        assertThatThrownBy(() -> bookAuditService.submit(bookId, authorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经提交过了");
        assertThat(countBy("book_audit_record", "book_id", bookId)).isEqualTo(1);
    }

    @Test
    @DisplayName("不能提交或下架他人的作品")
    void cannotOperateOthersBook() {
        long ownerId = insertAuthor("audit-owner");
        long otherId = insertAuthor("audit-intruder");
        long bookId = insertBook(ownerId, 0, PASSED);
        insertChapter(bookId, 1);

        assertThatThrownBy(() -> bookAuditService.submit(bookId, otherId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能操作自己的作品");
    }

    @Test
    @DisplayName("下架的书从书架和阅读历史里消失")
    void offlineBookDropsOutOfShelfAndHistory() {
        long authorId = insertAuthor("audit-shelf");
        long bookId = insertBook(authorId, 0, PASSED);
        long chapterId = insertChapter(bookId, 1);
        long userId = insertUser("audit-shelf-reader", 0);
        insertBookshelf(userId, bookId);
        insertHistory(userId, bookId, chapterId);

        assertThat(userBookshelfService.listByUserId(userId)).hasSize(1);
        assertThat(userReadHistoryService.listByUserId(userId)).hasSize(1);

        bookAuditService.takeDown(bookId, "收到侵权投诉", 1L);

        // 两条列表都联 book_info 过滤上架状态，下架后记录仍在表里，但读者看不到
        assertThat(userBookshelfService.listByUserId(userId)).isEmpty();
        assertThat(userReadHistoryService.listByUserId(userId)).isEmpty();
        assertThat(countBy("user_bookshelf", "book_id", bookId)).isEqualTo(1);
    }

    private long latestBookId(long authorId) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM book_info WHERE author_id = ?", Long.class, authorId);
        assertThat(id).isNotNull();
        return id;
    }

    private long latestRecordId(long bookId) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM book_audit_record WHERE book_id = ?", Long.class, bookId);
        assertThat(id).as("应已存在审核记录").isNotNull();
        return id;
    }

    private Integer auditStatusOf(long bookId) {
        return jdbcTemplate.queryForObject(
                "SELECT audit_status FROM book_info WHERE id = ?", Integer.class, bookId);
    }

    private String auditRemarkOf(long bookId) {
        return jdbcTemplate.queryForObject(
                "SELECT audit_remark FROM book_info WHERE id = ?", String.class, bookId);
    }

    private String bookNameOf(long bookId) {
        return jdbcTemplate.queryForObject(
                "SELECT book_name FROM book_info WHERE id = ?", String.class, bookId);
    }

}
