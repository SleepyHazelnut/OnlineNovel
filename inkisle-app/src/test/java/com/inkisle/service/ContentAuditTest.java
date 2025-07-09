package com.inkisle.service;

import com.inkisle.common.BusinessException;
import com.inkisle.entity.BookAuditRecord;
import com.inkisle.entity.BookChapter;
import com.inkisle.entity.BookComment;
import com.inkisle.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 章节与评论的审核
 * <p>
 * 两者的共同要求是：待审与驳回的内容读者看不到，作者本人能看到自己的；
 * 章节还多一条「作品过审时整本放行」，否则首次上架要一章一章点过去
 */
@Transactional
class ContentAuditTest extends BaseIntegrationTest {

    @Autowired
    private BookChapterService bookChapterService;

    @Autowired
    private ChapterAuditService chapterAuditService;

    @Autowired
    private BookCommentService bookCommentService;

    @Autowired
    private BookAuditService bookAuditService;

    @Test
    @DisplayName("新章节待审，读者目录看不到，作者能看到；通过后读者可见可读")
    void chapterNeedsAuditBeforeVisible() {
        long authorId = insertAuthor("audit-chapter");
        long bookId = insertBook(authorId, 0, 2);
        BookChapter chapter = saveChapter(bookId, authorId, "第一章", "正文内容");

        assertThat(bookChapterService.listByBookIdForAuthor(bookId, authorId).get(0).getAuditStatus())
                .isZero();
        assertThat(bookChapterService.listByBookId(bookId)).isEmpty();
        // 知道章节 id 也不能直接读未过审的正文
        assertThatThrownBy(() -> bookChapterService.read(chapter.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审核");

        chapterAuditService.pass(chapter.getId(), 1L);

        assertThat(bookChapterService.listByBookId(bookId)).hasSize(1);
        assertThat(bookChapterService.read(chapter.getId())).containsKey("content");
    }

    @Test
    @DisplayName("章节驳回后读者不可见，作者能看到驳回原因")
    void rejectedChapterIsHiddenToReader() {
        long authorId = insertAuthor("audit-chapter-reject");
        long bookId = insertBook(authorId, 0, 2);
        BookChapter chapter = saveChapter(bookId, authorId, "第一章", "内容");

        chapterAuditService.reject(chapter.getId(), "含有不适合的内容", 1L);

        assertThat(bookChapterService.listByBookId(bookId)).isEmpty();
        BookChapter forAuthor = bookChapterService.listByBookIdForAuthor(bookId, authorId).get(0);
        assertThat(forAuthor.getAuditStatus()).isEqualTo(2);
        assertThat(forAuthor.getRejectReason()).isEqualTo("含有不适合的内容");
    }

    @Test
    @DisplayName("已通过的章节修改后重新进入待审")
    void editedChapterBackToPending() {
        long authorId = insertAuthor("audit-chapter-edit");
        long bookId = insertBook(authorId, 0, 2);
        BookChapter chapter = saveChapter(bookId, authorId, "第一章", "原内容");
        chapterAuditService.pass(chapter.getId(), 1L);

        BookChapter edit = new BookChapter();
        edit.setId(chapter.getId());
        edit.setChapterName("第一章");
        bookChapterService.saveChapter(edit, "改过的内容", authorId);

        assertThat(bookChapterService.listByBookIdForAuthor(bookId, authorId).get(0).getAuditStatus())
                .isZero();
        assertThat(bookChapterService.listByBookId(bookId)).isEmpty();
    }

    @Test
    @DisplayName("作品过审时，其全部待审章节一并放行")
    void passingBookApprovesPendingChapters() {
        long authorId = insertAuthor("audit-book-pass");
        long bookId = insertBook(authorId, 0, 0);
        saveChapter(bookId, authorId, "第一章", "内容一");
        saveChapter(bookId, authorId, "第二章", "内容二");

        bookAuditService.submit(bookId, authorId);
        List<BookAuditRecord> pending = bookAuditService.listByStatus(0);
        Long recordId = pending.stream()
                .filter(r -> r.getBookId().equals(bookId))
                .findFirst().orElseThrow()
                .getId();
        bookAuditService.pass(recordId, 1L);

        assertThat(bookChapterService.listByBookId(bookId)).hasSize(2);
    }

    @Test
    @DisplayName("翻页跳过待审章节")
    void prevNextSkipPendingChapters() {
        long authorId = insertAuthor("audit-chapter-nav");
        long bookId = insertBook(authorId, 0, 2);
        BookChapter first = saveChapter(bookId, authorId, "第一章", "一");
        saveChapter(bookId, authorId, "第二章", "二");
        BookChapter third = saveChapter(bookId, authorId, "第三章", "三");

        chapterAuditService.pass(first.getId(), 1L);
        chapterAuditService.pass(third.getId(), 1L);
        // second 保持待审

        Map<String, Object> data = bookChapterService.read(first.getId());
        // 紧接着的第二章还没过审，下一章应直接指到第三章
        assertThat(data.get("nextId")).isEqualTo(third.getId());
        assertThat(bookChapterService.listByBookIdForAuthor(bookId, authorId).get(1).getAuditStatus())
                .isZero();
    }

    @Test
    @DisplayName("评论待审时只有自己能看到，通过后所有人可见")
    void commentVisibleAfterAudit() {
        long authorId = insertAuthor("audit-comment");
        long bookId = insertBook(authorId, 0, 2);
        long me = insertUser("audit-comment-me", 0);

        BookComment comment = new BookComment();
        comment.setBookId(bookId);
        comment.setUserId(me);
        comment.setContent("这本书不错");
        bookCommentService.add(comment);

        assertThat(bookCommentService.listByBookId(bookId, null)).isEmpty();
        assertThat(bookCommentService.listByBookId(bookId, me)).hasSize(1);

        bookCommentService.pass(comment.getId(), 1L);

        assertThat(bookCommentService.listByBookId(bookId, null)).hasSize(1);
    }

    @Test
    @DisplayName("评论驳回后作者仍能看到自己的评论与原因")
    void rejectedCommentVisibleToOwner() {
        long authorId = insertAuthor("audit-comment-reject");
        long bookId = insertBook(authorId, 0, 2);
        long me = insertUser("audit-comment-reject-me", 0);

        BookComment comment = new BookComment();
        comment.setBookId(bookId);
        comment.setUserId(me);
        comment.setContent("不合适的发言");
        bookCommentService.add(comment);

        bookCommentService.reject(comment.getId(), "请文明发言", 1L);

        assertThat(bookCommentService.listByBookId(bookId, null)).isEmpty();
        List<BookComment> mine = bookCommentService.listByBookId(bookId, me);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getRejectReason()).isEqualTo("请文明发言");
    }

    @Test
    @DisplayName("已审过的内容不能重复审核")
    void cannotAuditTwice() {
        long authorId = insertAuthor("audit-twice");
        long bookId = insertBook(authorId, 0, 2);
        BookChapter chapter = saveChapter(bookId, authorId, "第一章", "内容");

        chapterAuditService.pass(chapter.getId(), 1L);

        assertThatThrownBy(() -> chapterAuditService.pass(chapter.getId(), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经审过");
    }

    private BookChapter saveChapter(long bookId, long authorId, String name, String content) {
        BookChapter chapter = new BookChapter();
        chapter.setBookId(bookId);
        chapter.setChapterName(name);
        bookChapterService.saveChapter(chapter, content, authorId);
        return chapter;
    }

}
