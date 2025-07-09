package com.inkisle.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端分类维护的测试
 * <p>
 * 分类只有两级，删除又要保证不留下指向空分类的书，
 * 这几条约束都在服务层，这里验证它们能否从 HTTP 上正确触发，
 * 以及写接口是否确实只对管理员开放
 */
@Transactional
@AutoConfigureMockMvc
class AdminCategoryControllerTest extends BaseIntegrationTest {

    private static final Long ADMIN_ID = 9101L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @BeforeEach
    void prepareAdmin() {
        insertAdmin(ADMIN_ID, "ctrl-cat-admin");
    }

    @Test
    @DisplayName("非管理员不能调用分类维护接口")
    void nonAdminRejected() throws Exception {
        long authorId = insertAuthor("ctrl-cat-author");
        String token = "Bearer " + jwtUtil.generate(authorId, "ctrl-cat-author", "author");

        mockMvc.perform(post("/admin/category")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"越权分类\"}"))
                .andExpect(status().isForbidden());

        assertThat(countBy("book_category", "category_name", "越权分类")).isZero();
    }

    @Test
    @DisplayName("新增一级分类")
    void createRootCategory() throws Exception {
        mockMvc.perform(post("/admin/category")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"悬疑\",\"parentId\":0,\"sort\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(countBy("book_category", "category_name", "悬疑")).isEqualTo(1);
    }

    @Test
    @DisplayName("同级下重名被拒绝")
    void duplicateNameRejected() throws Exception {
        mockMvc.perform(post("/admin/category")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"玄幻\",\"parentId\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("同级下已有同名分类"));
    }

    @Test
    @DisplayName("不能把分类挂到二级分类下")
    void cannotNestThreeLevels() throws Exception {
        // 5 是「东方玄幻」，本身挂在玄幻之下
        mockMvc.perform(post("/admin/category")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"三级分类\",\"parentId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("分类只支持两级，不能挂到二级分类下"));
    }

    @Test
    @DisplayName("分类名不能为空")
    void blankNameRejected() throws Exception {
        mockMvc.perform(post("/admin/category")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("有子分类时不能删除")
    void cannotDeleteCategoryWithChildren() throws Exception {
        mockMvc.perform(delete("/admin/category/1")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("该分类下还有子分类，请先删除子分类"));

        assertThat(countBy("book_category", "id", 1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("分类下有未上架作品时也不能删除")
    void cannotDeleteCategoryUsedByUnpublishedBook() throws Exception {
        long categoryId = insertCategory("冷门分类", 0L);
        long authorId = insertAuthor("ctrl-cat-book");
        // 未提交状态：读者端查不到这本书，但分类引用是真实存在的，
        // 只看已上架作品的计数会漏掉它，删完分类这本书就挂空了
        jdbcTemplate.update(
                "INSERT INTO book_info (book_name, author_name, author_id, category_id, book_desc, "
                        + "cover_url, status, audit_status, create_time, update_time) "
                        + "VALUES ('未提交作品', '测试作者', ?, ?, '', '', 0, 0, NOW(), NOW())",
                authorId, categoryId);

        mockMvc.perform(delete("/admin/category/" + categoryId)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("该分类下还有作品，不能删除"));

        assertThat(countBy("book_category", "id", categoryId)).isEqualTo(1);
    }

    @Test
    @DisplayName("没有子分类且无作品引用时可以删除")
    void deleteEmptyCategory() throws Exception {
        long categoryId = insertCategory("临时分类", 0L);

        mockMvc.perform(delete("/admin/category/" + categoryId)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(countBy("book_category", "id", categoryId)).isZero();
    }

    @Test
    @DisplayName("修改分类名与排序")
    void updateCategory() throws Exception {
        long categoryId = insertCategory("待改名", 0L);

        mockMvc.perform(put("/admin/category/" + categoryId)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"改过名\",\"parentId\":0,\"sort\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(countBy("book_category", "category_name", "改过名")).isEqualTo(1);
    }

    @Test
    @DisplayName("读者端分类树能看到管理端新增的分类")
    void newCategoryVisibleInTree() throws Exception {
        insertCategory("新分类", 0L);

        mockMvc.perform(get("/category/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.categoryName == '新分类')]").exists());
    }

    private String adminToken() {
        return "Bearer " + jwtUtil.generate(ADMIN_ID, "ctrl-cat-admin", "admin");
    }

    private long insertCategory(String name, Long parentId) {
        jdbcTemplate.update(
                "INSERT INTO book_category (category_name, parent_id, sort) VALUES (?, ?, 0)",
                name, parentId);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM book_category WHERE category_name = ? ORDER BY id DESC LIMIT 1",
                Long.class, name);
        return id == null ? 0L : id;
    }

}
