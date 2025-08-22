package com.inkisle.service;

import com.inkisle.common.BusinessException;
import com.inkisle.entity.BookComment;
import com.inkisle.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 评论增删改
 * 重点还是归属，评论是读者自己写的，改和删都得看是不是本人
 * 一级评论删了回复要一起清，不然那些回复在列表里既查不到也删不掉
 */
@Transactional
class BookCommentServiceTest extends BaseIntegrationTest {

    @Autowired
    private BookCommentService bookCommentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("本人可以删除自己的评论")
    void ownerCanDelete() {
        long authorId = insertAuthor("cmt-del-ok");
        long bookId = insertBook(authorId, 0);
        long userId = insertUser("cmt-del-reader", 0);
        long commentId = insertCommentReturnId(bookId, userId, "路过", 0L);

        bookCommentService.delete(commentId, userId);

        assertThat(countBy("book_comment", "id", commentId)).isZero();
    }

    @Test
    @DisplayName("不能删除他人的评论，数据须保持不变")
    void cannotDeleteOthers() {
        long authorId = insertAuthor("cmt-del-no");
        long bookId = insertBook(authorId, 0);
        long ownerId = insertUser("cmt-owner", 0);
        long otherId = insertUser("cmt-other", 0);
        long commentId = insertCommentReturnId(bookId, ownerId, "路过", 0L);

        assertThatThrownBy(() -> bookCommentService.delete(commentId, otherId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("自己的评论");
        assertThat(countBy("book_comment", "id", commentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("删除一级评论时其下回复一并删除")
    void deleteRootAlsoRemovesReplies() {
        long authorId = insertAuthor("cmt-cascade");
        long bookId = insertBook(authorId, 0);
        long userId = insertUser("cmt-cascade-user", 0);
        long rootId = insertCommentReturnId(bookId, userId, "一级评论", 0L);
        long replyId = insertCommentReturnId(bookId, userId, "回复", rootId);

        bookCommentService.delete(rootId, userId);

        assertThat(countBy("book_comment", "id", rootId)).isZero();
        assertThat(countBy("book_comment", "id", replyId)).isZero();
    }

    @Test
    @DisplayName("删除回复不影响一级评论")
    void deleteReplyKeepsRoot() {
        long authorId = insertAuthor("cmt-del-reply");
        long bookId = insertBook(authorId, 0);
        long userId = insertUser("cmt-del-reply-user", 0);
        long rootId = insertCommentReturnId(bookId, userId, "一级评论", 0L);
        long replyId = insertCommentReturnId(bookId, userId, "回复", rootId);

        bookCommentService.delete(replyId, userId);

        assertThat(countBy("book_comment", "id", replyId)).isZero();
        assertThat(countBy("book_comment", "id", rootId)).isEqualTo(1);
    }

    @Test
    @DisplayName("本人可以修改评论内容，前后空格会被去掉")
    void ownerCanUpdateContent() {
        long authorId = insertAuthor("cmt-edit-ok");
        long bookId = insertBook(authorId, 0);
        long userId = insertUser("cmt-edit-user", 0);
        long commentId = insertCommentReturnId(bookId, userId, "旧内容", 0L);

        bookCommentService.updateContent(commentId, userId, "  新内容  ");

        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM book_comment WHERE id = ?", String.class, commentId);
        assertThat(content).isEqualTo("新内容");
    }

    @Test
    @DisplayName("不能修改他人的评论")
    void cannotUpdateOthers() {
        long authorId = insertAuthor("cmt-edit-no");
        long bookId = insertBook(authorId, 0);
        long ownerId = insertUser("cmt-edit-owner", 0);
        long otherId = insertUser("cmt-edit-other", 0);
        long commentId = insertCommentReturnId(bookId, ownerId, "原文", 0L);

        assertThatThrownBy(() -> bookCommentService.updateContent(commentId, otherId, "改掉"))
                .isInstanceOf(BusinessException.class);
        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM book_comment WHERE id = ?", String.class, commentId);
        assertThat(content).isEqualTo("原文");
    }

    @Test
    @DisplayName("空内容与超长内容都不接受")
    void contentIsValidated() {
        long authorId = insertAuthor("cmt-valid");
        long bookId = insertBook(authorId, 0);
        long userId = insertUser("cmt-valid-user", 0);
        long commentId = insertCommentReturnId(bookId, userId, "原文", 0L);

        assertThatThrownBy(() -> bookCommentService.updateContent(commentId, userId, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> bookCommentService.updateContent(commentId, userId, "x".repeat(501)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("回复不存在的评论会被拒绝")
    void cannotReplyToMissingComment() {
        long authorId = insertAuthor("cmt-reply-missing");
        long bookId = insertBook(authorId, 0);
        long userId = insertUser("cmt-reply-missing-user", 0);

        BookComment comment = new BookComment();
        comment.setBookId(bookId);
        comment.setUserId(userId);
        comment.setContent("回复");
        comment.setParentId(999999L);

        assertThatThrownBy(() -> bookCommentService.add(comment))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("不能回复其他作品下的评论")
    void cannotReplyAcrossBooks() {
        long authorId = insertAuthor("cmt-reply-cross");
        long bookA = insertBook(authorId, 0);
        long bookB = insertBook(authorId, 0);
        long userId = insertUser("cmt-reply-cross-user", 0);
        long commentOfA = insertCommentReturnId(bookA, userId, "A 书评论", 0L);

        BookComment comment = new BookComment();
        comment.setBookId(bookB);
        comment.setUserId(userId);
        comment.setContent("跨书回复");
        comment.setParentId(commentOfA);

        assertThatThrownBy(() -> bookCommentService.add(comment))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于这本书");
    }

    @Test
    @DisplayName("对回复再回复时挂到一级评论下，列表里不会丢")
    void replyToReplyFallsBackToRoot() {
        long authorId = insertAuthor("cmt-two-level");
        long bookId = insertBook(authorId, 0, 2);
        long userId = insertUser("cmt-two-level-user", 0);
        long rootId = insertCommentReturnId(bookId, userId, "一级评论", 0L);
        long replyId = insertCommentReturnId(bookId, userId, "回复", rootId);

        BookComment third = new BookComment();
        third.setBookId(bookId);
        third.setUserId(userId);
        third.setContent("回复的回复");
        third.setParentId(replyId);
        bookCommentService.add(third);

        // 父级还指着 replyId 的话，拼树的时候它既不是根也挂不到根下，这条就查不出来了
        // 这些评论都是待审状态，传 viewerId 才能看到自己发的
        assertThat(bookCommentService.listByBookId(bookId, userId))
                .flatExtracting(BookComment::getChildren)
                .hasSize(2);
    }

    @Test
    @DisplayName("我的评论只返回自己的，按时间倒序")
    void pageByUserOnlyReturnsOwn() {
        long authorId = insertAuthor("cmt-mine");
        long bookId = insertBook(authorId, 0);
        long me = insertUser("cmt-mine-me", 0);
        long other = insertUser("cmt-mine-other", 0);
        insertCommentReturnId(bookId, me, "第一条", 0L);
        insertCommentReturnId(bookId, me, "第二条", 0L);
        insertCommentReturnId(bookId, other, "别人的", 0L);

        Map<String, Object> page = bookCommentService.pageByUser(me, 1, 10);

        assertThat(page.get("total")).isEqualTo(2);
        assertThat((java.util.List<?>) page.get("list")).hasSize(2);
    }

    @Test
    @DisplayName("管理端按关键字过滤评论")
    void adminSearchByKeyword() {
        long authorId = insertAuthor("cmt-search");
        long bookId = insertBook(authorId, 0);
        long userId = insertUser("cmt-search-user", 0);
        insertCommentReturnId(bookId, userId, "这本书真好笑", 0L);
        insertCommentReturnId(bookId, userId, "更新太慢了", 0L);

        Map<String, Object> hit = bookCommentService.pageAll("真好笑", null, 1, 10);

        assertThat(hit.get("total")).isEqualTo(1);
    }

    private long insertCommentReturnId(long bookId, long userId, String content, Long parentId) {
        jdbcTemplate.update(
                "INSERT INTO book_comment (book_id, user_id, content, parent_id, create_time) "
                        + "VALUES (?, ?, ?, ?, NOW())",
                bookId, userId, content, parentId);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM book_comment WHERE book_id = ? AND user_id = ? AND content = ? "
                        + "ORDER BY id DESC LIMIT 1", Long.class, bookId, userId, content);
        return id == null ? 0L : id;
    }

}
