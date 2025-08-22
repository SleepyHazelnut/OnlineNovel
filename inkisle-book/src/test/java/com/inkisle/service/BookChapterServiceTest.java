package com.inkisle.service;

import com.inkisle.common.BusinessException;
import com.inkisle.entity.BookChapter;
import com.inkisle.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 章节服务的回归测试
 * 重点在归属：章节接口是按 id 操作的，bookId 又是请求体给的，
 * 只验请求体里那个 bookId 的话，拿自己的书过校验、改别人的章节就混进来了
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

    @Test
    @DisplayName("追加章节按当前最大章节号递增")
    void appendChapterIncrementsNum() {
        long authorId = insertAuthor("chapter-append");
        long bookId = insertBook(authorId, 0);

        BookChapter first = new BookChapter();
        first.setBookId(bookId);
        first.setChapterName("第一章");
        bookChapterService.saveChapter(first, "正文一", authorId);

        BookChapter second = new BookChapter();
        second.setBookId(bookId);
        second.setChapterName("第二章");
        bookChapterService.saveChapter(second, "正文二", authorId);

        assertThat(chapterNumOf(first.getId())).isEqualTo(1);
        assertThat(chapterNumOf(second.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("同一本书内章节号不允许重复，不同书之间互不影响")
    void chapterNumIsUniqueWithinBook() {
        long authorId = insertAuthor("chapter-unique");
        long bookId = insertBook(authorId, 0);
        insertChapter(bookId, 1);

        // 唯一索引兜底：即使代码里的锁失效，也不会有两个第 1 章
        assertThatThrownBy(() -> insertChapter(bookId, 1))
                .isInstanceOf(DuplicateKeyException.class);

        long otherBookId = insertBook(authorId, 0);
        assertThat(insertChapter(otherBookId, 1)).isPositive();
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
