package com.inkisle.controller;

import com.inkisle.support.BaseIntegrationTest;
import com.inkisle.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 拦截器的两类问题：角色串号与封禁延迟生效
 * <p>
 * 两者都只在 HTTP 层暴露——服务层拿到的已经是拦截器传下来的 userId，
 * 放行与否根本不在它的视野里，所以只能在这里测
 * <p>
 * 探测路径用本服务的接口：账号状态在用户服务，这里按 stub 给值
 */
@Transactional
@AutoConfigureMockMvc
class LoginInterceptorTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("作者 token 不能调用读者写接口")
    void authorCannotUseReaderApi() throws Exception {
        long authorId = insertAuthor("itc-author");
        long bookId = insertBook(authorId, 0, 2);
        String token = "Bearer " + jwtUtil.generate(authorId, "itc-author", "author");

        mockMvc.perform(post("/comment/add")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + bookId + ",\"content\":\"作者发的评论\"}"))
                .andExpect(status().isForbidden());

        // 被拒之后不该留下任何以作者 id 落库的评论
        assertThat(countBy("book_comment", "user_id", authorId)).isZero();
    }

    @Test
    @DisplayName("读者可以正常发言，评论记在自己名下")
    void readerCanComment() throws Exception {
        long authorId = insertAuthor("itc-author2");
        long bookId = insertBook(authorId, 0, 2);
        long userId = insertUser("itc-reader", 0);
        String token = "Bearer " + jwtUtil.generate(userId, "itc-reader", "user");

        mockMvc.perform(post("/comment/add")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + bookId + ",\"content\":\"读者发的评论\"}"))
                .andExpect(status().isOk());

        assertThat(countBy("book_comment", "user_id", userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("被封禁的账号，已签发的 token 立即失效")
    void bannedUserTokenStopsWorking() throws Exception {
        long userId = insertUser("itc-banned", 1);
        String token = "Bearer " + jwtUtil.generate(userId, "itc-banned", "user");

        Mockito.when(userClient.status("user", userId)).thenReturn(1);
        mockMvc.perform(get("/comment/mine").header("Authorization", token))
                .andExpect(status().isForbidden());

        // 解封后同一个 token 应当恢复可用：状态是每次请求现查的，没做缓存
        Mockito.when(userClient.status("user", userId)).thenReturn(0);
        mockMvc.perform(get("/comment/mine").header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("账号记录被删除后 token 同样失效")
    void deletedAccountTokenStopsWorking() throws Exception {
        long userId = insertUser("itc-deleted", 0);
        String token = "Bearer " + jwtUtil.generate(userId, "itc-deleted", "user");

        // 用户服务查不到记录时返回 null，与封禁同样对待
        Mockito.when(userClient.status("user", userId)).thenReturn(null);
        mockMvc.perform(get("/comment/mine").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("没有 token 时拒绝，游客可读接口不受影响")
    void anonymousAccess() throws Exception {
        mockMvc.perform(get("/comment/mine"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/book/list"))
                .andExpect(status().isOk());
    }

}
