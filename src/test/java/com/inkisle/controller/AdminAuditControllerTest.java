package com.inkisle.controller;

import com.inkisle.service.BookService;
import com.inkisle.support.BaseIntegrationTest;
import com.inkisle.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端审核接口的测试
 * <p>
 * 服务层已经覆盖了状态流转，这里补的是接口这一层：路径是否落在管理端鉴权范围内、
 * 参数怎么解析、下架与预览是否真的能从 HTTP 上走通
 */
@Transactional
@AutoConfigureMockMvc
class AdminAuditControllerTest extends BaseIntegrationTest {

    private static final Long ADMIN_ID = 9001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private BookService bookService;

    /**
     * 拦截器现在每次请求都回查账号是否存在，token 里的 9001 必须在表里，
     * 否则所有管理端用例都会先被 403 挡掉
     */
    @BeforeEach
    void prepareAdmin() {
        insertAdmin(ADMIN_ID, "ctrl-admin");
    }

    @Test
    @DisplayName("作者访问审核接口应被拒绝")
    void authorCannotAccessAdminAudit() throws Exception {
        long authorId = insertAuthor("ctrl-author");
        String token = "Bearer " + jwtUtil.generate(authorId, "ctrl-author", "author");

        mockMvc.perform(get("/admin/audit/books")
                        .param("auditStatus", "2")
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员可以按上架状态查书")
    void adminCanListBooksByAuditStatus() throws Exception {
        long authorId = insertAuthor("ctrl-list");
        long bookId = insertBook(authorId, 0, 2);

        mockMvc.perform(get("/admin/audit/books")
                        .param("auditStatus", "2")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(bookId))
                .andExpect(jsonPath("$.data.list[0].auditStatus").value(2));
    }

    @Test
    @DisplayName("审核预览返回作品信息与章节目录，正文按章取")
    void adminCanPreviewBookContent() throws Exception {
        long authorId = insertAuthor("ctrl-preview");
        long bookId = insertBook(authorId, 0, 1);
        long chapterId = insertChapter(bookId, 1);
        insertContent(chapterId, "第一章正文");

        mockMvc.perform(get("/admin/audit/preview")
                        .param("bookId", String.valueOf(bookId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.book.id").value(bookId))
                .andExpect(jsonPath("$.data.chapters.length()").value(1));

        mockMvc.perform(get("/admin/audit/chapter")
                        .param("bookId", String.valueOf(bookId))
                        .param("chapterId", String.valueOf(chapterId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("第一章正文"));
    }

    @Test
    @DisplayName("预览正文时不能拿别的作品的章节 id 来读")
    void cannotPreviewChapterOfAnotherBook() throws Exception {
        long authorId = insertAuthor("ctrl-cross");
        long bookId = insertBook(authorId, 0, 1);
        long otherBookId = insertBook(authorId, 0, 1);
        long otherChapterId = insertChapter(otherBookId, 1);
        insertContent(otherChapterId, "别人的正文");

        mockMvc.perform(get("/admin/audit/chapter")
                        .param("bookId", String.valueOf(bookId))
                        .param("chapterId", String.valueOf(otherChapterId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("章节不属于这本书"));
    }

    @Test
    @DisplayName("下架必须写原因，下架后读者看不到")
    void takeDownRequiresReasonAndHidesBook() throws Exception {
        long authorId = insertAuthor("ctrl-takedown");
        long bookId = insertBook(authorId, 0, 2);

        // 先正常读一次详情，让这条记录进详情缓存；下架后若不失效，
        // 下面那条断言会读到缓存里的旧状态而失败
        mockMvc.perform(get("/book/" + bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/admin/audit/take-down")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + bookId + ",\"remark\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("下架要写原因"));
        assertThat(bookService.isVisibleToReader(bookId)).isTrue();

        mockMvc.perform(post("/admin/audit/take-down")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + bookId + ",\"remark\":\"收到侵权投诉\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(bookService.isVisibleToReader(bookId)).isFalse();

        // 缓存已失效，详情接口当场就读不到这本书
        mockMvc.perform(get("/book/" + bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("作品不存在或未上架"));

        mockMvc.perform(post("/admin/audit/restore")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + bookId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(bookService.isVisibleToReader(bookId)).isTrue();
    }

    @Test
    @DisplayName("只能查已上架或已下架，待审的书走审核记录列表")
    void rejectsUnsupportedAuditStatus() throws Exception {
        mockMvc.perform(get("/admin/audit/books")
                        .param("auditStatus", "1")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("只能查已上架或已下架的作品"));
    }

    private String adminToken() {
        return "Bearer " + jwtUtil.generate(ADMIN_ID, "admin", "admin");
    }

}
