package com.inkisle.service;

import com.inkisle.common.BusinessException;
import com.inkisle.entity.BookInfo;
import com.inkisle.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 作品的管理动作：管理员删书、作者自主下架
 * 这两件事的影响面比作者编辑大得多，边界必须钉住：
 * 管理员能删任意书且关联数据清干净，作者只能动自己的书
 */
@Transactional
class BookManageTest extends BaseIntegrationTest {

    @Autowired
    private BookService bookService;

    @Autowired
    private BookAuditService bookAuditService;

    @Test
    @DisplayName("管理员可以删除任意作者的作品，关联数据一并清理")
    void adminCanDeleteAnyBook() {
        long authorId = insertAuthor("manage-admin-del");
        long bookId = insertBook(authorId, 0, 2);
        long chapterId = insertChapter(bookId, 1);
        insertContent(chapterId, "正文");
        insertComment(bookId, 1L);
        insertBookshelf(1L, bookId);

        bookService.adminDelete(bookId);

        assertThat(countBy("book_info", "id", bookId)).isZero();
        assertThat(countBy("book_chapter", "book_id", bookId)).isZero();
        assertThat(countBy("book_content", "chapter_id", chapterId)).isZero();
        assertThat(countBy("book_comment", "book_id", bookId)).isZero();
        assertThat(countBy("user_bookshelf", "book_id", bookId)).isZero();
    }

    @Test
    @DisplayName("作者下架自己的作品后状态变为已下架并留下说明")
    void authorCanTakeOwnBookOffline() {
        long authorId = insertAuthor("manage-offline");
        long bookId = insertBook(authorId, 0, 2);

        bookAuditService.offlineByAuthor(bookId, authorId);

        BookInfo book = bookService.getById(bookId);
        assertThat(book.getAuditStatus()).isEqualTo(4);
        assertThat(book.getAuditRemark()).isEqualTo("作者自主下架");
    }

    @Test
    @DisplayName("不能下架别人的作品")
    void cannotTakeDownAnotherAuthorsBook() {
        long ownerId = insertAuthor("manage-owner");
        long otherId = insertAuthor("manage-other");
        long bookId = insertBook(ownerId, 0, 2);

        assertThatThrownBy(() -> bookAuditService.offlineByAuthor(bookId, otherId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能操作自己的作品");

        assertThat(bookService.getById(bookId).getAuditStatus()).isEqualTo(2);
    }

    @Test
    @DisplayName("未上架的作品不能下架")
    void cannotTakeDownUnpublishedBook() {
        long authorId = insertAuthor("manage-draft");
        long bookId = insertBook(authorId, 0, 0);

        assertThatThrownBy(() -> bookAuditService.offlineByAuthor(bookId, authorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已上架");
    }

}
