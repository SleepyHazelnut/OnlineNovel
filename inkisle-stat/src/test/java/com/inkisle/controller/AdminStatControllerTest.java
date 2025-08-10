package com.inkisle.controller;

import com.inkisle.common.RoleConstant;
import com.inkisle.support.BaseStatTest;
import com.inkisle.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统计接口的访问控制
 * 服务拆出来之后拦截器是从单体照搬的，这里就确认三件事：
 * 没带 token 进不去、读者进不去、管理员能拿到数
 */
@Transactional
@AutoConfigureMockMvc
class AdminStatControllerTest extends BaseStatTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("不带 token 返回 401")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/admin/stat/total"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("读者 token 访问后台统计返回 403")
    void readerIsForbidden() throws Exception {
        String token = jwtUtil.generate(1L, "reader1", RoleConstant.USER);

        mockMvc.perform(get("/admin/stat/total").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员可以拿到总量与按天数据")
    void adminSeesStat() throws Exception {
        String token = jwtUtil.generate(1L, "admin", RoleConstant.ADMIN);

        mockMvc.perform(get("/admin/stat/total").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userTotal").isNumber());

        mockMvc.perform(get("/admin/stat/daily").param("days", "7")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

}
