package com.inkisle.support;

import com.inkisle.client.AuditClient;
import com.inkisle.client.UserClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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
 * 服务层集成测试基类
 * 连真 MySQL，这个项目的关键逻辑都在 SQL 里（级联删除的顺序、全文索引、事务边界），
 * 换内存库或者 mock 掉 mapper 就测不出来了
 * 子类每个测试方法加 @Transactional，跑完回滚，用例之间不影响
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

    @Autowired
    protected CacheManager cacheManager;

    /**
     * 缓存不跟着事务回滚：用例里读过的数据会留在进程内的 Map 里，
     * 下一个用例撞上同一个 key 就会读到上个用例的对象。
     * 这类脏读在断言里表现为「明明插了库却读到空」，很难往缓存上想
     */
    @BeforeEach
    void clearCache() {
        for (String name : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    /** 本机没起 Redis 时 Redisson 建连失败，测试里换成 mock */
    @MockBean
    protected RedissonClient redissonClient;

    /**
     * 送审、清审核记录都改走 Feign 了，测试里没有审核服务，
     * 不换掉会直接 503：服务发现里找不到 inkisle-audit
     */
    @MockBean
    protected AuditClient auditClient;

    /**
     * 账号状态也在用户服务，测试里换成 mock
     * 默认按正常账号返回，要验封禁的用例自己再 stub 一遍
     */
    @MockBean
    protected UserClient userClient;

    @BeforeEach
    void stubAccountStatus() {
        Mockito.when(userClient.status(Mockito.anyString(), Mockito.anyLong())).thenReturn(0);
    }

    /**
     * 两处都靠 Redis，测试里统一换成 mock：
     * 防重复提交切面走占坑令牌（getBucket + trySet），要直接放行；
     * 缓存回源走互斥锁（getLock），要能抢到并且能重入释放。
     * 漏掉任何一处，接口都会在执行到 Redis 那行时空指针
     */
    @BeforeEach
    void allowRepeatSubmit() throws InterruptedException {
        RBucket<String> bucket = Mockito.mock(RBucket.class);
        Mockito.when(bucket.trySet(Mockito.any(), Mockito.anyLong(), Mockito.any())).thenReturn(true);
        // getBucket 声明成 RBucket<V>，按字符串取值时泛型对不上，这里按擦除后的类型塞进去
        Mockito.when(redissonClient.<String>getBucket(Mockito.anyString())).thenReturn(bucket);

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
