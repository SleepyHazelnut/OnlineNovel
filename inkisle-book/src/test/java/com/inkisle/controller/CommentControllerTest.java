package com.inkisle.controller;

import com.inkisle.support.BaseIntegrationTest;
import com.inkisle.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评论接口
 * 归属校验服务层已经测过，这里补接口这层：路径要不要登录、
 * 编辑的请求体能不能解析、删别人的评论时错误是不是统一结构
 */
@Transactional
@AutoConfigureMockMvc
class CommentControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("未登录访问我的评论应被拒绝")
    void anonymousCannotReadMine() throws Exception {
        mockMvc.perform(get("/comment/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登录后可分页查看自己的评论")
    void readerCanReadMine() throws Exception {
        long authorId = insertAuthor("ctrl-cmt-author");
        long bookId = insertBook(authorId, 0);
        long userId = insertUser("ctrl-cmt-reader", 0);
        insertComment(bookId, userId);

        mockMvc.perform(get("/comment/mine")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .header("Authorization", bearer(userId, "ctrl-cmt-reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("修改自己的评论成功，改他人的被拒绝")
    void updateOwnCommentOnly() throws Exception {
        long authorId = insertAuthor("ctrl-cmt-edit-author");
        long bookId = insertBook(authorId, 0);
        long ownerId = insertUser("ctrl-cmt-edit-owner", 0);
        long otherId = insertUser("ctrl-cmt-edit-other", 0);
        long commentId = insertCommentId(bookId, ownerId);

        mockMvc.perform(put("/comment/" + commentId)
                        .header("Authorization", bearer(ownerId, "ctrl-cmt-edit-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"改过之后\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(put("/comment/" + commentId)
                        .header("Authorization", bearer(otherId, "ctrl-cmt-edit-other"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"我要改\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("只能修改或删除自己的评论"));
    }

    @Test
    @DisplayName("内容为空时由参数校验拦下")
    void blankContentRejected() throws Exception {
        long authorId = insertAuthor("ctrl-cmt-blank-author");
        long bookId = insertBook(authorId, 0);
        long userId = insertUser("ctrl-cmt-blank", 0);
        long commentId = insertCommentId(bookId, userId);

        mockMvc.perform(put("/comment/" + commentId)
                        .header("Authorization", bearer(userId, "ctrl-cmt-blank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                // 参数校验失败现在给的是 HTTP 400
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("作者与读者都不能调用管理端评论接口")
    void nonAdminCannotUseAdminCommentApi() throws Exception {
        long authorId = insertAuthor("ctrl-cmt-nonadmin");
        long adminlessId = insertUser("ctrl-cmt-reader2", 0);

        mockMvc.perform(get("/admin/comment/list")
                        .header("Authorization", bearer(authorId, "ctrl-cmt-nonadmin")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/admin/comment/1")
                        .header("Authorization", bearer(adminlessId, "ctrl-cmt-reader2")))
                .andExpect(status().isForbidden());
    }

    private String bearer(long userId, String username) {
        return "Bearer " + jwtUtil.generate(userId, username, "user");
    }

    private long insertCommentId(long bookId, long userId) {
        insertComment(bookId, userId);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM book_comment WHERE book_id = ? AND user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, bookId, userId);
        return id == null ? 0L : id;
    }

}
