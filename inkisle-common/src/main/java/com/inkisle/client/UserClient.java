package com.inkisle.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 查账号状态。三张账号表都归用户服务，别的服务不查那些表
 */
@FeignClient(name = "inkisle-user")
public interface UserClient {

    /**
     * 按角色查状态：0 正常，1 封禁，null 表示账号不存在
     * <p>
     * 管理员没有状态列，停用靠删记录，故只区分在与不在
     */
    @GetMapping("/internal/user/status")
    Integer status(@RequestParam("role") String role, @RequestParam("id") Long id);

}
