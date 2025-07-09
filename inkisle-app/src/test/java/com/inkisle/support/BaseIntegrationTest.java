package com.inkisle.support;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务层集成测试的基类
 * <p>
 * 走真实 MySQL：本项目的关键逻辑在 SQL 里（级联删除的子查询顺序、全文索引、
 * 事务边界），用内存库或 mock 掉 mapper 都验证不到，测了也不算数
 * <p>
 * 每个测试方法由子类加 `@Transactional`，结束时整体回滚，用例之间互不干扰
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestCacheConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    /** 建表脚本整个 JVM 只需执行一次，多个测试类共享同一个 Spring 上下文 */
    private static final AtomicBoolean SCHEMA_READY = new AtomicBoolean(false);

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** 本机没起 Redis 时 Redisson 建连失败，测试里换成 mock */
    @MockBean
    protected RedissonClient redissonClient;

    /** 防重复提交切面直接放行，测试要验的是接口本身 */
    @BeforeEach
    void allowRepeatSubmit() throws InterruptedException {
        RLock lock = Mockito.mock(RLock.class);
        Mockito.when(lock.tryLock(Mockito.anyLong(), Mockito.anyLong(), Mockito.any())).thenReturn(true);
        Mockito.when(lock.isHeldByCurrentThread()).thenReturn(true);
        Mockito.when(redissonClient.getLock(Mockito.anyString())).thenReturn(lock);
    }

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

    /**
     * 管理员按指定主键插入：接口测试里 token 的 sub 是写死的，
     * 自增出来的 id 对不上就会被拦截器判成「账号不存在」
     */
    protected long insertAdmin(long id, String username) {
        jdbcTemplate.update(
                "INSERT INTO admin_info (id, username, password, real_name, create_time, update_time) "
                        + "VALUES (?, ?, ?, '测试管理员', NOW(), NOW())",
                id, username, "e10adc3949ba59abbe56e057f20f883e");
        return id;
    }

    /** 读者，status 传 1 即封禁状态 */
    protected long insertUser(String username, int status) {
        return insertAndReturnId(
                "INSERT INTO user_info (username, password, nick_name, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, NOW(), NOW())",
                username, "e10adc3949ba59abbe56e057f20f883e", username, status);
    }

    protected long insertBook(long authorId, int status) {
        // 默认按「未提交」写入，与新建作品的真实状态一致；
        // 需要已上架的书时用三参数重载显式指定
        return insertBook(authorId, status, 0);
    }

    protected long insertBook(long authorId, int status, int auditStatus) {
        return insertAndReturnId(
                "INSERT INTO book_info (book_name, author_name, author_id, category_id, book_desc, cover_url, status, audit_status, create_time, update_time) "
                        + "VALUES (?, '测试作者', ?, 1, '简介', '', ?, ?, NOW(), NOW())",
                "测试作品-" + authorId, authorId, status, auditStatus);
    }

    /**
     * 直接落库一个已过审的章节
     * 走 saveChapter 会按正常流程置为待审，读者读不到；
     * 大多数用例关心的是章节之外的行为，这里默认给一个可读的章节，
     * 需要待审场景的用例请走 Service 保存
     */
    protected long insertChapter(long bookId, int chapterNum) {
        return insertAndReturnId(
                "INSERT INTO book_chapter (book_id, chapter_name, chapter_num, audit_status, create_time) "
                        + "VALUES (?, ?, ?, 1, NOW())",
                bookId, "第" + chapterNum + "章", chapterNum);
    }

    protected void insertContent(long chapterId, String content) {
        jdbcTemplate.update("INSERT INTO book_content (chapter_id, content) VALUES (?, ?)", chapterId, content);
    }

    protected void insertComment(long bookId, long userId) {
        jdbcTemplate.update(
                "INSERT INTO book_comment (book_id, user_id, content, parent_id, create_time) VALUES (?, ?, '路过', 0, NOW())",
                bookId, userId);
    }

    protected void insertBookshelf(long userId, long bookId) {
        jdbcTemplate.update(
                "INSERT INTO user_bookshelf (user_id, book_id, create_time) VALUES (?, ?, NOW())", userId, bookId);
    }

    protected void insertHistory(long userId, long bookId, long chapterId) {
        jdbcTemplate.update(
                "INSERT INTO user_read_history (user_id, book_id, chapter_id, chapter_num, update_time) VALUES (?, ?, ?, 1, NOW())",
                userId, bookId, chapterId);
    }

    protected void insertAuditRecord(long bookId, long authorId, int status) {
        jdbcTemplate.update(
                "INSERT INTO book_audit_record (book_id, author_id, status, submit_time) VALUES (?, ?, ?, NOW())",
                bookId, authorId, status);
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
