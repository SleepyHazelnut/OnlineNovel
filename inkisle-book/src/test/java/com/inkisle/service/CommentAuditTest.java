package com.inkisle.service;

import com.inkisle.entity.BookComment;
import com.inkisle.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评论审核
 * 跟章节不一样，评论归书籍服务，审的时候不用跨服务
 * 待审的只有本人看得见，驳回后本人还得能看到原因，不然等于悄悄删帖
 */
@Transactional
class CommentAuditTest extends BaseIntegrationTest {

    @Autowired
    private BookCommentService bookCommentService;

    @Test
    @DisplayName("评论待审时只有自己能看到，通过后所有人可见")
    void commentVisibleAfterAudit() {
        long authorId = insertAuthor("audit-comment");
        long bookId = insertBook(authorId, 0, 2);
        long me = insertUser("audit-comment-me", 0);

        BookComment comment = insert(bookId, me, "这本书不错");

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

        BookComment comment = insert(bookId, me, "不合适的发言");

        bookCommentService.reject(comment.getId(), "请文明发言", 1L);

        assertThat(bookCommentService.listByBookId(bookId, null)).isEmpty();
        List<BookComment> mine = bookCommentService.listByBookId(bookId, me);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getRejectReason()).isEqualTo("请文明发言");
    }

    private BookComment insert(long bookId, long userId, String content) {
        BookComment comment = new BookComment();
        comment.setBookId(bookId);
        comment.setUserId(userId);
        comment.setContent(content);
        bookCommentService.add(comment);
        return comment;
    }

}
