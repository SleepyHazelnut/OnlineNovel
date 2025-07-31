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
 * 章节审核。状态本身在书籍服务，本服务只决定它该是什么，
 * 改完之后落到哪一列由书籍服务那边负责，这里只看最终结果
 */
@Transactional
class ChapterAuditServiceTest extends BaseIntegrationTest {

    /** 与 ContentAuditStatus 一致 */
    private static final int PENDING = 0;
    private static final int PASSED = 1;
    private static final int REJECTED = 2;

    @Autowired
    private ChapterAuditService chapterAuditService;

    @Test
    @DisplayName("待审章节通过后变为已通过")
    void passMakesChapterVisible() {
        long authorId = insertAuthor("ch-pass");
        long bookId = insertBook(authorId, 0, 2);
        long chapterId = insertChapter(bookId, 1);

        chapterAuditService.pass(chapterId, 99L);

        assertThat(auditStatusOf(chapterId)).isEqualTo(PASSED);
    }

    @Test
    @DisplayName("驳回后章节不可见，原因留给作者看")
    void rejectedChapterKeepsReason() {
        long authorId = insertAuthor("ch-reject");
        long bookId = insertBook(authorId, 0, 2);
        long chapterId = insertChapter(bookId, 1);

        chapterAuditService.reject(chapterId, "含有不适合的内容", 99L);

        assertThat(auditStatusOf(chapterId)).isEqualTo(REJECTED);
        assertThat(rejectReasonOf(chapterId)).isEqualTo("含有不适合的内容");
    }

    @Test
    @DisplayName("驳回必须写原因")
    void rejectRequiresReason() {
        long authorId = insertAuthor("ch-reject-reason");
        long bookId = insertBook(authorId, 0, 2);
        long chapterId = insertChapter(bookId, 1);

        assertThatThrownBy(() -> chapterAuditService.reject(chapterId, " ", 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("驳回要写原因");
        assertThat(auditStatusOf(chapterId)).isEqualTo(PENDING);
    }

    @Test
    @DisplayName("已审过的章节不能重复审")
    void cannotAuditTwice() {
        long authorId = insertAuthor("ch-twice");
        long bookId = insertBook(authorId, 0, 2);
        long chapterId = insertChapter(bookId, 1);
        chapterAuditService.pass(chapterId, 99L);

        assertThatThrownBy(() -> chapterAuditService.pass(chapterId, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经审过");

        // 驳回也算审过，不能审完通过再驳回
        assertThatThrownBy(() -> chapterAuditService.reject(chapterId, "反悔了", 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经审过");
    }

    @Test
    @DisplayName("章节不存在时报错，不是静默放行")
    void missingChapterIsRejected() {
        assertThatThrownBy(() -> chapterAuditService.pass(999999L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("章节不存在");
    }

    @Test
    @DisplayName("审核详情带上正文，便于管理端直接看")
    void detailCarriesContent() {
        long authorId = insertAuthor("ch-detail");
        long bookId = insertBook(authorId, 0, 2);
        long chapterId = insertChapter(bookId, 1);
        insertContent(chapterId, "第一章的正文");

        assertThat(chapterAuditService.detail(chapterId))
                .containsEntry("content", "第一章的正文");
    }

    private Integer auditStatusOf(long chapterId) {
        return jdbcTemplate.queryForObject(
                "SELECT audit_status FROM book_chapter WHERE id = ?", Integer.class, chapterId);
    }

    private String rejectReasonOf(long chapterId) {
        return jdbcTemplate.queryForObject(
                "SELECT reject_reason FROM book_chapter WHERE id = ?", String.class, chapterId);
    }

}
