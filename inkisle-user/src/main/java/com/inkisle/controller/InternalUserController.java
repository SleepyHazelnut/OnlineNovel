package com.inkisle.controller;

import com.inkisle.common.InternalControllerSupport;
import com.inkisle.service.support.AccountStatusResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 给别的服务查账号状态
 * 三张账号表各管各的自增，同一个 id 在读者、作者、管理员里是三个人，
 * 所以必须带角色一起传，光给 id 会查错表
 */
@RestController
@RequestMapping("/internal/user")
public class InternalUserController extends InternalControllerSupport {

    @Autowired
    private AccountStatusResolver accountStatusResolver;

    /**
     * 0 正常，1 封禁，账号不存在返回 null
     * 不缓存：缓存会让封禁延迟生效
     */
    @GetMapping("/status")
    public Integer status(@RequestParam String role, @RequestParam Long id) {
        return accountStatusResolver.resolve(role, id);
    }

}
