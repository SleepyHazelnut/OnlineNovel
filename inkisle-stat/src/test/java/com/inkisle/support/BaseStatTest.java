package com.inkisle.support;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 统计服务集成测试的基类
 * 全是聚合 SQL，把 mapper mock 掉就只剩「有没有调过」，没意思
 * 真正会出问题的是 GROUP BY、DATE_SUB、补零这几处，所以连真库
 * 用例加 @Transactional，跑完回滚
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseStatTest {

    private static final AtomicBoolean SCHEMA_READY = new AtomicBoolean(false);

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeAll
    void prepareSchema() {
        if (SCHEMA_READY.compareAndSet(false, true)) {
            SchemaLoader.load(dataSource);
        }
    }

    /** daysAgo 为 0 表示今天 */
    protected long insertUser(String username, int daysAgo) {
        jdbcTemplate.update(
                "INSERT INTO user_info (username, password, nick_name, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, 0, DATE_SUB(NOW(), INTERVAL ? DAY), NOW())",
                username, "e10adc3949ba59abbe56e057f20f883e", username, daysAgo);
        return jdbcTemplate.queryForObject("SELECT id FROM user_info WHERE username = ?", Long.class, username);
    }

    protected long insertAuthor(String username, int daysAgo) {
        jdbcTemplate.update(
                "INSERT INTO author_info (username, password, pen_name, audit_status, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, 1, 0, DATE_SUB(NOW(), INTERVAL ? DAY), NOW())",
                username, "e10adc3949ba59abbe56e057f20f883e", username, daysAgo);
        return jdbcTemplate.queryForObject("SELECT id FROM author_info WHERE username = ?", Long.class, username);
    }

    protected long insertBook(long authorId, String name, int daysAgo) {
        jdbcTemplate.update(
                "INSERT INTO book_info (book_name, author_name, author_id, category_id, status, audit_status, "
                        + "create_time, update_time) VALUES (?, '测试作者', ?, 1, 0, 2, "
                        + "DATE_SUB(NOW(), INTERVAL ? DAY), NOW())",
                name, authorId, daysAgo);
        return jdbcTemplate.queryForObject("SELECT id FROM book_info WHERE book_name = ?", Long.class, name);
    }

    protected void insertComment(long bookId, long userId, int daysAgo) {
        jdbcTemplate.update(
                "INSERT INTO book_comment (book_id, user_id, content, parent_id, audit_status, create_time) "
                        + "VALUES (?, ?, '路过', 0, 1, DATE_SUB(NOW(), INTERVAL ? DAY))",
                bookId, userId, daysAgo);
    }

    protected int count(String table) {
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

}
