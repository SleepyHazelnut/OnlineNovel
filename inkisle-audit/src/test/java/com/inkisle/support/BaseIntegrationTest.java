package com.inkisle.support;

import com.inkisle.client.BookClient;
import com.inkisle.dto.AuditStatusDTO;
import com.inkisle.entity.BookChapter;
import com.inkisle.entity.BookInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 审核服务集成测试的基类
 * 作品和章节的状态列在书籍库，审核服务改它们得走 Feign。测试里没起书籍服务，
 * 直接调是 503，所以只把 HTTP 那层换成 mock，落库还是真的
 * 库是共用的，与其断言「调了一次接口」，不如断言改完表里是什么
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

    @MockBean
    protected BookClient bookClient;

    @BeforeAll
    void prepareSchema() {
        if (SCHEMA_READY.compareAndSet(false, true)) {
            SchemaLoader.load(dataSource);
        }
    }

    @BeforeEach
    void stubBookClient() {
        // 读作品
        Mockito.when(bookClient.getBook(Mockito.anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return jdbcTemplate.query(
                    "SELECT id, author_id, book_name, audit_status, audit_remark FROM book_info WHERE id = ?",
                    rs -> {
                        if (!rs.next()) {
                            return null;
                        }
                        BookInfo book = new BookInfo();
                        book.setId(rs.getLong("id"));
                        book.setAuthorId(rs.getLong("author_id"));
                        book.setBookName(rs.getString("book_name"));
                        book.setAuditStatus(rs.getInt("audit_status"));
                        book.setAuditRemark(rs.getString("audit_remark"));
                        return book;
                    }, id);
        });

        // 改作品状态
        Mockito.doAnswer(inv -> {
            Long id = inv.getArgument(0);
            AuditStatusDTO dto = inv.getArgument(1);
            jdbcTemplate.update("UPDATE book_info SET audit_status = ?, audit_remark = ? WHERE id = ?",
                    dto.getStatus(), dto.getRemark(), id);
            return null;
        }).when(bookClient).updateBookStatus(Mockito.anyLong(), Mockito.any());

        // 置为待审，返回影响行数
        Mockito.when(bookClient.markPending(Mockito.anyLong())).thenAnswer(inv -> jdbcTemplate.update(
                "UPDATE book_info SET audit_status = 1 WHERE id = ? AND audit_status = 0",
                (Object) inv.getArgument(0)));

        // 整本放行
        Mockito.doAnswer(inv -> {
            jdbcTemplate.update("UPDATE book_chapter SET audit_status = 1 WHERE book_id = ? AND audit_status = 0",
                    (Object) inv.getArgument(0));
            return null;
        }).when(bookClient).passAllChapters(Mockito.anyLong());

        // 读章节
        Mockito.when(bookClient.getChapter(Mockito.anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return jdbcTemplate.query(
                    "SELECT id, book_id, chapter_name, chapter_num, audit_status, reject_reason "
                            + "FROM book_chapter WHERE id = ?",
                    rs -> {
                        if (!rs.next()) {
                            return null;
                        }
                        BookChapter chapter = new BookChapter();
                        chapter.setId(rs.getLong("id"));
                        chapter.setBookId(rs.getLong("book_id"));
                        chapter.setChapterName(rs.getString("chapter_name"));
                        chapter.setChapterNum(rs.getInt("chapter_num"));
                        chapter.setAuditStatus(rs.getInt("audit_status"));
                        chapter.setRejectReason(rs.getString("reject_reason"));
                        return chapter;
                    }, id);
        });

        // 改章节状态
        Mockito.doAnswer(inv -> {
            Long id = inv.getArgument(0);
            AuditStatusDTO dto = inv.getArgument(1);
            jdbcTemplate.update("UPDATE book_chapter SET audit_status = ?, reject_reason = ? WHERE id = ?",
                    dto.getStatus(), dto.getRemark(), id);
            return null;
        }).when(bookClient).updateChapterStatus(Mockito.anyLong(), Mockito.any());

        Mockito.when(bookClient.listChapters(Mockito.anyLong())).thenAnswer(inv -> jdbcTemplate.query(
                "SELECT id, book_id, chapter_name, chapter_num, audit_status FROM book_chapter "
                        + "WHERE book_id = ? ORDER BY chapter_num",
                (rs, row) -> {
                    BookChapter chapter = new BookChapter();
                    chapter.setId(rs.getLong("id"));
                    chapter.setBookId(rs.getLong("book_id"));
                    chapter.setChapterName(rs.getString("chapter_name"));
                    chapter.setChapterNum(rs.getInt("chapter_num"));
                    chapter.setAuditStatus(rs.getInt("audit_status"));
                    return chapter;
                }, (Object) inv.getArgument(0)));

        Mockito.when(bookClient.getChapterContent(Mockito.anyLong())).thenAnswer(inv -> {
            Map<String, Object> data = new java.util.HashMap<>();
            List<String> contents = jdbcTemplate.queryForList(
                    "SELECT content FROM book_content WHERE chapter_id = ?", String.class, (Object) inv.getArgument(0));
            data.put("content", contents.isEmpty() ? "" : contents.get(0));
            return data;
        });
    }

    protected long insertAuthor(String username) {
        return insertAndReturnId(
                "INSERT INTO author_info (username, password, pen_name, intro, audit_status, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, '', 1, 0, NOW(), NOW())",
                username, "e10adc3949ba59abbe56e057f20f883e", username);
    }

    /** 管理员按指定主键插入：接口测试里 token 的 sub 是写死的 */
    protected long insertAdmin(long id, String username) {
        jdbcTemplate.update(
                "INSERT INTO admin_info (id, username, password, real_name, create_time, update_time) "
                        + "VALUES (?, ?, ?, '测试管理员', NOW(), NOW())",
                id, username, "e10adc3949ba59abbe56e057f20f883e");
        return id;
    }

    protected long insertUser(String username, int status) {
        return insertAndReturnId(
                "INSERT INTO user_info (username, password, nick_name, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, NOW(), NOW())",
                username, "e10adc3949ba59abbe56e057f20f883e", username, status);
    }

    protected long insertBook(long authorId, int status) {
        return insertBook(authorId, status, 0);
    }

    protected long insertBook(long authorId, int status, int auditStatus) {
        return insertAndReturnId(
                "INSERT INTO book_info (book_name, author_name, author_id, category_id, book_desc, cover_url, status, audit_status, create_time, update_time) "
                        + "VALUES (?, '测试作者', ?, 1, '简介', '', ?, ?, NOW(), NOW())",
                "测试作品-" + authorId, authorId, status, auditStatus);
    }

    /** 待审的章节，与作者端新建后未经审核的状态一致 */
    protected long insertChapter(long bookId, int chapterNum) {
        return insertAndReturnId(
                "INSERT INTO book_chapter (book_id, chapter_name, chapter_num, audit_status, create_time) "
                        + "VALUES (?, ?, ?, 0, NOW())",
                bookId, "第" + chapterNum + "章", chapterNum);
    }

    protected void insertContent(long chapterId, String content) {
        jdbcTemplate.update("INSERT INTO book_content (chapter_id, content) VALUES (?, ?)", chapterId, content);
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
