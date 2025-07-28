package com.inkisle.service;

import com.inkisle.common.BusinessException;
import com.inkisle.dto.HomeBookDTO;
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
 * 首页推荐位的回归测试
 * 推荐位只存 book_id，展示字段靠联表取，最容易出问题的是「书下架了推荐位还露在外面」
 */
@Transactional
class HomeBookServiceTest extends BaseIntegrationTest {

    @Autowired
    private HomeBookService homeBookService;

    @Autowired
    private BookService bookService;

    @Test
    @DisplayName("加入推荐位后读者端可以看到")
    void addedBookAppearsInRecommend() {
        long authorId = insertAuthor("home-add");
        long bookId = insertBook(authorId, 0, 2);

        homeBookService.add(dto(bookId, 1, 10));

        List<Map<String, Object>> hot = hotList();
        assertThat(hot).hasSize(1);
        assertThat(hot.get(0).get("bookName")).isEqualTo("测试作品-" + authorId);
    }

    @Test
    @DisplayName("未上架的作品不能加入推荐位")
    void cannotAddUnpublishedBook() {
        long authorId = insertAuthor("home-draft");
        long bookId = insertBook(authorId, 0, 0);

        assertThatThrownBy(() -> homeBookService.add(dto(bookId, 1, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已上架");
    }

    @Test
    @DisplayName("同一本书在同一板块只能加入一次")
    void cannotAddTwice() {
        long authorId = insertAuthor("home-dup");
        long bookId = insertBook(authorId, 0, 2);

        homeBookService.add(dto(bookId, 1, 0));

        assertThatThrownBy(() -> homeBookService.add(dto(bookId, 1, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已在该板块");
    }

    @Test
    @DisplayName("作品下架后推荐位不再对外展示，但管理端仍能看到该条目")
    void offlineBookIsHiddenToReader() {
        long authorId = insertAuthor("home-offline");
        long bookId = insertBook(authorId, 0, 2);
        homeBookService.add(dto(bookId, 2, 0));

        jdbcTemplate.update("UPDATE book_info SET audit_status = 4 WHERE id = ?", bookId);

        assertThat((List<?>) homeBookService.recommend().get("quality")).isEmpty();
        // 管理员需要看到这条，才能把它从推荐位里清掉
        assertThat(homeBookService.listBySection(2)).hasSize(1);
    }

    @Test
    @DisplayName("排序值大的排在前面")
    void sortOrdersList() {
        long authorId = insertAuthor("home-sort");
        long first = insertBook(authorId, 0, 2);
        long second = insertBook(authorId, 0, 2);

        homeBookService.add(dto(first, 1, 1));
        homeBookService.add(dto(second, 1, 9));

        List<Map<String, Object>> hot = hotList();
        assertThat(hot).hasSize(2);
        assertThat(((Number) hot.get(0).get("bookId")).longValue()).isEqualTo(second);
    }

    @Test
    @DisplayName("删除作品时推荐位一并清理")
    void deleteRemovesHomeBook() {
        long authorId = insertAuthor("home-del");
        long bookId = insertBook(authorId, 0, 2);
        homeBookService.add(dto(bookId, 1, 0));

        bookService.delete(bookId, authorId);

        assertThat(countBy("home_book", "book_id", bookId)).isZero();
    }

    private HomeBookDTO dto(long bookId, int section, int sort) {
        HomeBookDTO dto = new HomeBookDTO();
        dto.setBookId(bookId);
        dto.setSection(section);
        dto.setSort(sort);
        return dto;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> hotList() {
        return (List<Map<String, Object>>) homeBookService.recommend().get("hot");
    }

}
