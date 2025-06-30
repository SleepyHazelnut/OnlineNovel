package com.inkisle.service;

import com.inkisle.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 点击、新书、更新三个榜单的回归测试
 * 这三个榜单直接按 book_info 的列排序，没有缓存层，
 * 需要验证的是：排序正确、未上架作品被排除、非法的 top 不会让 SQL 报错
 */
@Transactional
class RankServiceTest extends BaseIntegrationTest {

    @Autowired
    private RankService rankService;

    @Test
    @DisplayName("点击榜按累计阅读次数倒序，未上架作品不参与")
    void clickRankOrdersByVisitCount() {
        long authorId = insertAuthor("rank-click");
        long low = insertBook(authorId, 0, 2);
        long high = insertBook(authorId, 0, 2);
        long draft = insertBook(authorId, 0, 0);

        jdbcTemplate.update("UPDATE book_info SET visit_count = 3 WHERE id = ?", low);
        jdbcTemplate.update("UPDATE book_info SET visit_count = 30 WHERE id = ?", high);
        jdbcTemplate.update("UPDATE book_info SET visit_count = 300 WHERE id = ?", draft);

        List<Map<String, Object>> list = rankService.clickList(10);

        assertThat(list).hasSize(2);
        assertThat(bookIdOf(list.get(0))).isEqualTo(high);
        assertThat(bookIdOf(list.get(1))).isEqualTo(low);
        assertThat(list.get(0).get("rank")).isEqualTo(1);
    }

    @Test
    @DisplayName("新书榜按创建时间倒序")
    void newRankOrdersByCreateTime() {
        long authorId = insertAuthor("rank-new");
        long older = insertBook(authorId, 0, 2);
        long newer = insertBook(authorId, 0, 2);

        jdbcTemplate.update("UPDATE book_info SET create_time = '2025-01-01 10:00:00' WHERE id = ?", older);
        jdbcTemplate.update("UPDATE book_info SET create_time = '2025-06-01 10:00:00' WHERE id = ?", newer);

        List<Map<String, Object>> list = rankService.newList(10);

        assertThat(bookIdOf(list.get(0))).isEqualTo(newer);
        assertThat(bookIdOf(list.get(1))).isEqualTo(older);
    }

    @Test
    @DisplayName("更新榜按最近更新时间倒序")
    void updateRankOrdersByUpdateTime() {
        long authorId = insertAuthor("rank-update");
        long stale = insertBook(authorId, 0, 2);
        long fresh = insertBook(authorId, 0, 2);

        jdbcTemplate.update("UPDATE book_info SET update_time = '2025-01-01 10:00:00' WHERE id = ?", stale);
        jdbcTemplate.update("UPDATE book_info SET update_time = '2025-09-01 10:00:00' WHERE id = ?", fresh);

        List<Map<String, Object>> list = rankService.updateList(10);

        assertThat(bookIdOf(list.get(0))).isEqualTo(fresh);
        assertThat(bookIdOf(list.get(1))).isEqualTo(stale);
    }

    @Test
    @DisplayName("top 传 0 或负数不会拼出 LIMIT 负数，按一条处理")
    void topIsClamped() {
        long authorId = insertAuthor("rank-top");
        insertBook(authorId, 0, 2);

        assertThat(rankService.clickList(0)).hasSize(1);
        assertThat(rankService.clickList(-5)).hasSize(1);
        // 超过上限也只取到库里有的条数，不报错
        assertThat(rankService.clickList(999)).hasSize(1);
    }

    private long bookIdOf(Map<String, Object> item) {
        return ((Number) item.get("bookId")).longValue();
    }

}
