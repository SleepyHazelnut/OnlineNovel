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
     * 按角色查状态，0 正常 1 封禁，null 是没这个账号
     * 管理员表没有状态列，停用即删除记录，因此只判断记录是否存在
     */
    @GetMapping("/internal/user/status")
    Integer status(@RequestParam("role") String role, @RequestParam("id") Long id);

}
