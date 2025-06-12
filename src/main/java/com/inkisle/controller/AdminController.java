package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.entity.AdminInfo;
import com.inkisle.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端入口，管理员不开放注册，账号在库里直接加
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    /**
     * 管理端登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody AdminInfo adminInfo) {
        return adminService.login(adminInfo.getUsername(), adminInfo.getPassword());
    }

    /**
     * 当前登录管理员
     */
    @GetMapping("/info")
    public Result<AdminInfo> info(HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        return Result.ok(adminService.getById(adminId));
    }

}
