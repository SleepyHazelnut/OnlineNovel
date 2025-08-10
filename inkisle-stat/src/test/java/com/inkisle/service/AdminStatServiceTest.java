package com.inkisle.service;

import com.inkisle.support.BaseStatTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 看板统计的服务层用例
 * 一个接口是四表 COUNT，另一个是三表按天聚合再合并
 * 容易错的是某张表当天没数据要补零、合并完日期还保不保持有序，这两条用真库验
 */
@Transactional
class AdminStatServiceTest extends BaseStatTest {

    @Autowired
    private AdminStatService adminStatService;

    @Test
    @DisplayName("总量与四张表各自的行数一致")
    void totalCountsEveryTable() {
        long authorId = insertAuthor("stat-author", 1);
        insertBook(authorId, "统计用的书", 1);

        Map<String, Object> total = adminStatService.total();

        assertThat(number(total, "userTotal")).isEqualTo(count("user_info"));
        assertThat(number(total, "authorTotal")).isEqualTo(count("author_info"));
        assertThat(number(total, "bookTotal")).isEqualTo(count("book_info"));
        assertThat(number(total, "commentTotal")).isEqualTo(count("book_comment"));
    }

    @Test
    @DisplayName("某张表当天没有数据时，合并结果里该列补 0")
    void dailyFillsZeroForMissingTable() {
        insertUser("stat-user-only", 1);

        List<Map<String, Object>> rows = adminStatService.daily(7);

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row).containsKeys("day", "userNum", "bookNum", "commentNum");
            // 本用例只插了用户，另外两列必须是 0 而不是缺键
            assertThat(number(row, "bookNum")).isZero();
        });
    }

    @Test
    @DisplayName("三张表的日期交错时，合并结果仍按日期升序")
    void dailyIsSortedByDay() {
        insertUser("stat-user-old", 5);
        long authorId = insertAuthor("stat-author-new", 1);
        insertBook(authorId, "较新的书", 1);

        List<Map<String, Object>> rows = adminStatService.daily(7);

        // 先按日期取出来，再判断是否单调不减
        List<String> days = rows.stream().map(r -> String.valueOf(r.get("day"))).toList();
        assertThat(days).isSorted();

        // 5 天前那条与 1 天前那条都要在，且前者排在后者前面
        int oldIndex = indexOfDay(rows, 5);
        int newIndex = indexOfDay(rows, 1);
        assertThat(oldIndex).isGreaterThanOrEqualTo(0);
        assertThat(newIndex).isGreaterThan(oldIndex);
    }

    @Test
    @DisplayName("统计范围按天数截断，超出窗口的数据不计入")
    void dailyRespectsWindow() {
        long authorId = insertAuthor("stat-author-long-ago", 30);
        insertBook(authorId, "一个月前的书", 30);

        List<Map<String, Object>> rows = adminStatService.daily(7);

        assertThat(indexOfDay(rows, 30)).isEqualTo(-1);
    }

    /** 把 days 天前换算成 yyyy-MM-dd 再在结果里找，找不到返回 -1 */
    private int indexOfDay(List<Map<String, Object>> rows, int daysAgo) {
        String target = java.time.LocalDate.now().minusDays(daysAgo).toString();
        for (int i = 0; i < rows.size(); i++) {
            if (target.equals(String.valueOf(rows.get(i).get("day")))) {
                return i;
            }
        }
        return -1;
    }

    /** MySQL 的 COUNT 返回 Long，日期列却是 java.sql.Date，统一按数字取 */
    private long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        assertThat(value).as("字段 %s 不应为 null", key).isNotNull();
        return ((Number) value).longValue();
    }

}
