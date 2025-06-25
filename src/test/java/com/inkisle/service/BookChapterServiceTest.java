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
 * 章节服务的回归测试
 * <p>
 * 重点是归属校验：章节接口按 id 操作，而 bookId 由请求体给出，
 * 只校验请求体里的 bookId 会留下「用自己的书过校验、改别人的章节」的口子
 */
@Transactional
class BookChapterServiceTest extends BaseIntegrationTest {

    @Autowired
    private BookChapterService bookChapterService;

    @Test
    @DisplayName("不能借自己的 bookId 修改他人作品的章节")
    void cannotUpdateChapterOfAnotherAuthor() {
        long ownerId = insertAuthor("chapter-owner");
        long attackerId = insertAuthor("chapter-attacker");
        long targetBookId = insertBook(ownerId, 0);
        long targetChapterId = insertChapter(targetBookId, 1);
        insertContent(targetChapterId, "原作者写的正文");

        // 攻击者拿自己名下的一本书，用它的 id 发起修改
        long ownBookId = insertBook(attackerId, 0);

        BookChapter patch = new BookChapter();
        patch.setId(targetChapterId);
        patch.setBookId(ownBookId);
        patch.setChapterName("被篡改的标题");

        assertThatThrownBy(() -> bookChapterService.saveChapter(patch, "被篡改的正文", attackerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能管理自己作品的章节");

        assertThat(contentOf(targetChapterId)).isEqualTo("原作者写的正文");
        assertThat(chapterNameOf(targetChapterId)).isEqualTo("第1章");
    }

    @Test
    @DisplayName("作者可以修改自己作品的章节")
    void canUpdateOwnChapter() {
        long authorId = insertAuthor("chapter-self");
        long bookId = insertBook(authorId, 0);
        long chapterId = insertChapter(bookId, 1);
        insertContent(chapterId, "旧正文");

        BookChapter patch = new BookChapter();
        patch.setId(chapterId);
        patch.setBookId(bookId);
        patch.setChapterName("改名后的章节");

        bookChapterService.saveChapter(patch, "新正文", authorId);

        assertThat(contentOf(chapterId)).isEqualTo("新正文");
        assertThat(chapterNameOf(chapterId)).isEqualTo("改名后的章节");
        // 章节号不受编辑影响，否则目录顺序会乱
        assertThat(chapterNumOf(chapterId)).isEqualTo(1);
    }

    @Test
    @DisplayName("作者端目录不能读他人作品的章节")
    void cannotListChaptersOfAnotherAuthor() {
        long ownerId = insertAuthor("list-owner");
        long otherId = insertAuthor("list-other");
        long bookId = insertBook(ownerId, 0);
        insertChapter(bookId, 1);

        assertThatThrownBy(() -> bookChapterService.listByBookIdForAuthor(bookId, otherId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("删除章节时一并清掉指向该章的阅读进度")
    void deleteChapterAlsoClearsReadHistory() {
        long authorId = insertAuthor("chapter-delete");
        long bookId = insertBook(authorId, 0);
        long chapterId = insertChapter(bookId, 1);
        insertContent(chapterId, "正文");
        insertHistory(1L, bookId, chapterId);

        bookChapterService.deleteChapter(chapterId, authorId);

        assertThat(countBy("book_chapter", "id", chapterId)).isZero();
        assertThat(countBy("book_content", "chapter_id", chapterId)).isZero();
        assertThat(countBy("user_read_history", "chapter_id", chapterId)).isZero();
    }

    private String contentOf(long chapterId) {
        return jdbcTemplate.queryForObject(
                "SELECT content FROM book_content WHERE chapter_id = ?", String.class, chapterId);
    }

    private String chapterNameOf(long chapterId) {
        return jdbcTemplate.queryForObject(
                "SELECT chapter_name FROM book_chapter WHERE id = ?", String.class, chapterId);
    }

    private Integer chapterNumOf(long chapterId) {
        return jdbcTemplate.queryForObject(
                "SELECT chapter_num FROM book_chapter WHERE id = ?", Integer.class, chapterId);
    }

}
