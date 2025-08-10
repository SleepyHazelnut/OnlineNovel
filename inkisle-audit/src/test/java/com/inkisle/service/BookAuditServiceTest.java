package com.inkisle.service;

import com.inkisle.common.BusinessException;
import com.inkisle.entity.BookAuditRecord;
import com.inkisle.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 作品审核的回归测试
 * 记录表在本服务，作品和章节的状态得调书籍服务改，两边不在一个事务里，
 * 所以每个用例两头都看：记录表的流转，还有书和章节最后落在哪个状态
 */
@Transactional
class BookAuditServiceTest extends BaseIntegrationTest {

    /** 与 BookAuditStatus 一致 */
    private static final int DRAFT = 0;
    private static final int PENDING = 1;
    private static final int PASSED = 2;
    private static final int REJECTED = 3;
    private static final int OFFLINE = 4;

    @Autowired
    private BookAuditService bookAuditService;

    @Test
    @DisplayName("提交审核：作品进待审，落一条待审记录")
    void submitCreatesPendingRecord() {
        long authorId = insertAuthor("audit-submit");
        long bookId = insertBook(authorId, 0);
        insertChapter(bookId, 1);

        bookAuditService.submit(bookId, authorId);

        assertThat(auditStatusOf(bookId)).isEqualTo(PENDING);
        assertThat(countBy("book_audit_record", "book_id", bookId)).isEqualTo(1);
    }

    @Test
    @DisplayName("通过后作品上架，待审章节一并放行")
    void passPutsBookOnlineAndApprovesChapters() {
        long authorId = insertAuthor("audit-pass");
        long bookId = insertBook(authorId, 0);
        insertChapter(bookId, 1);
        insertChapter(bookId, 2);
        bookAuditService.submit(bookId, authorId);

        bookAuditService.pass(latestRecordId(bookId), 99L);

        assertThat(auditStatusOf(bookId)).isEqualTo(PASSED);
        // 整本书一起过，不必一章一章点
        assertThat(countBy("book_chapter", "book_id", bookId)).isEqualTo(2);
        assertThat(countOfPassedChapters(bookId)).isEqualTo(2);
    }

    @Test
    @DisplayName("驳回后作品不可见，原因写到作品上供作者查看")
    void rejectKeepsReasonOnBook() {
        long authorId = insertAuthor("audit-reject");
        long bookId = insertBook(authorId, 0);
        insertChapter(bookId, 1);
        bookAuditService.submit(bookId, authorId);

        bookAuditService.reject(latestRecordId(bookId), "内容不符合要求", 99L);

        assertThat(auditStatusOf(bookId)).isEqualTo(REJECTED);
        assertThat(auditRemarkOf(bookId)).isEqualTo("内容不符合要求");
        assertThat(recordStatusOf(latestRecordId(bookId))).isEqualTo(2);
    }

    @Test
    @DisplayName("驳回必须写原因")
    void rejectRequiresReason() {
        long authorId = insertAuthor("audit-reject-reason");
        long bookId = insertBook(authorId, 0);
        insertChapter(bookId, 1);
        bookAuditService.submit(bookId, authorId);

        assertThatThrownBy(() -> bookAuditService.reject(latestRecordId(bookId), "  ", 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("驳回要写原因");
    }

    @Test
    @DisplayName("待审期间重复提交会被挡住，只留一条记录")
    void cannotSubmitTwice() {
        long authorId = insertAuthor("audit-twice");
        long bookId = insertBook(authorId, 0);
        insertChapter(bookId, 1);
        bookAuditService.submit(bookId, authorId);

        assertThatThrownBy(() -> bookAuditService.submit(bookId, authorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作品已在审核中");
        assertThat(countBy("book_audit_record", "book_id", bookId)).isEqualTo(1);
    }

    @Test
    @DisplayName("没有章节的作品不能提交审核")
    void cannotSubmitBookWithoutChapter() {
        long authorId = insertAuthor("audit-empty");
        long bookId = insertBook(authorId, 0);

        assertThatThrownBy(() -> bookAuditService.submit(bookId, authorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少发布一章");
        assertThat(auditStatusOf(bookId)).isEqualTo(DRAFT);
    }

    @Test
    @DisplayName("已审过的记录不能重复审")
    void cannotAuditTwice() {
        long authorId = insertAuthor("audit-again");
        long bookId = insertBook(authorId, 0);
        insertChapter(bookId, 1);
        bookAuditService.submit(bookId, authorId);
        bookAuditService.pass(latestRecordId(bookId), 99L);

        assertThatThrownBy(() -> bookAuditService.pass(latestRecordId(bookId), 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经审过");
    }

    @Test
    @DisplayName("作者只能操作自己的作品")
    void cannotOperateOthersBook() {
        long ownerId = insertAuthor("audit-owner");
        long otherId = insertAuthor("audit-intruder");
        long bookId = insertBook(ownerId, 0, PASSED);
        insertChapter(bookId, 1);

        assertThatThrownBy(() -> bookAuditService.submit(bookId, otherId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能操作自己的作品");
        assertThatThrownBy(() -> bookAuditService.offlineByAuthor(bookId, otherId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能操作自己的作品");
    }

    @Test
    @DisplayName("改了展示信息重新送审：连改两次也只留一条待审记录")
    void resubmitAfterEditKeepsOneRecord() {
        long authorId = insertAuthor("audit-resubmit");
        long bookId = insertBook(authorId, 0, PASSED);

        bookAuditService.resubmitAfterEdit(bookId, authorId);
        bookAuditService.resubmitAfterEdit(bookId, authorId);

        assertThat(auditStatusOf(bookId)).isEqualTo(PENDING);
        assertThat(countBy("book_audit_record", "book_id", bookId)).isEqualTo(1);
    }

    @Test
    @DisplayName("下架要写原因，且只有已上架的能下架")
    void takeDownRequiresReasonAndOnlineBook() {
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
    @DisplayName("下架后恢复上架，下架原因要清掉")
    void takeDownThenRestore() {
        long authorId = insertAuthor("audit-restore");
        long bookId = insertBook(authorId, 0, PASSED);

        bookAuditService.takeDown(bookId, "收到侵权投诉", 99L);
        assertThat(auditStatusOf(bookId)).isEqualTo(OFFLINE);
        assertThat(auditRemarkOf(bookId)).isEqualTo("收到侵权投诉");

        bookAuditService.restore(bookId, 99L);
        assertThat(auditStatusOf(bookId)).isEqualTo(PASSED);
        // 不清的话作者一直看到一条过期的说明
        assertThat(auditRemarkOf(bookId)).isNull();
    }

    @Test
    @DisplayName("下架不写审核记录：它不是一次送审")
    void takeDownDoesNotWriteRecord() {
        long authorId = insertAuthor("audit-takedown-record");
        long bookId = insertBook(authorId, 0, PASSED);

        bookAuditService.takeDown(bookId, "收到侵权投诉", 99L);

        assertThat(countBy("book_audit_record", "book_id", bookId)).isZero();
    }

    @Test
    @DisplayName("作者自主下架，说明要写清楚是谁下架的")
    void authorTakesBookOffline() {
        long authorId = insertAuthor("audit-self-offline");
        long bookId = insertBook(authorId, 0, PASSED);

        bookAuditService.offlineByAuthor(bookId, authorId);

        assertThat(auditStatusOf(bookId)).isEqualTo(OFFLINE);
        assertThat(auditRemarkOf(bookId)).isEqualTo("作者自主下架");
    }

    @Test
    @DisplayName("作品删除时清掉它的审核记录")
    void deleteByBookIdRemovesRecords() {
        long authorId = insertAuthor("audit-delete");
        long bookId = insertBook(authorId, 0);
        insertChapter(bookId, 1);
        bookAuditService.submit(bookId, authorId);
        assertThat(countBy("book_audit_record", "book_id", bookId)).isEqualTo(1);

        bookAuditService.deleteByBookId(bookId);

        assertThat(countBy("book_audit_record", "book_id", bookId)).isZero();
    }

    @Test
    @DisplayName("待审列表只返回还没审的记录")
    void pendingListOnlyContainsWaitingOnes() {
        long authorId = insertAuthor("audit-list");
        long pendingBook = insertBook(authorId, 0);
        long passedBook = insertBook(authorId, 0);
        insertChapter(pendingBook, 1);
        insertChapter(passedBook, 1);
        bookAuditService.submit(pendingBook, authorId);
        bookAuditService.submit(passedBook, authorId);
        bookAuditService.pass(latestRecordId(passedBook), 99L);

        List<BookAuditRecord> pending = bookAuditService.listByStatus(0);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getBookId()).isEqualTo(pendingBook);
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

    private Integer recordStatusOf(long recordId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM book_audit_record WHERE id = ?", Integer.class, recordId);
    }

    private int countOfPassedChapters(long bookId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM book_chapter WHERE book_id = ? AND audit_status = 1", Integer.class, bookId);
        return count == null ? 0 : count;
    }

}
