package com.inkisle.support;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用户服务集成测试的基类
 * 连真 MySQL，注册的唯一约束、签到位图的读写都在 SQL 里，换内存库或者 mock 掉 mapper 就测不出来
 * 子类每个测试方法加 @Transactional，跑完回滚
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    /** 建表脚本整个 JVM 只需执行一次，多个测试类共享同一个 Spring 上下文 */
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

    protected long insertAuthor(String username) {
        return insertAndReturnId(
                "INSERT INTO author_info (username, password, pen_name, intro, audit_status, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, '', 1, 0, NOW(), NOW())",
                username, "e10adc3949ba59abbe56e057f20f883e", username);
    }

    protected long insertUser(String username, int status) {
        return insertAndReturnId(
                "INSERT INTO user_info (username, password, nick_name, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, NOW(), NOW())",
                username, "e10adc3949ba59abbe56e057f20f883e", username, status);
    }

    protected int countBy(String table, String column, Object value) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
        return count == null ? 0 : count;
    }

    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入未返回主键：" + sql);
        }
        return key.longValue();
    }

}
