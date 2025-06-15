package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端的用户管理。读者端原有一个 /user/list，已收口至此，
 * 挂在 `/admin` 前缀下，由拦截器统一限制为仅管理员可访问
 */
@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    @Autowired
    private UserService userService;

    /**
     * 用户列表，分页返回
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(userService.page(pageNum, pageSize));
    }

    /**
     * 封禁 / 解封：status 传 1 封禁、0 恢复
     */
    @PostMapping("/status")
    public Result<Void> updateStatus(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        Integer status = Integer.valueOf(params.get("status").toString());
        userService.updateStatus(userId, status);
        return Result.ok();
    }

}
