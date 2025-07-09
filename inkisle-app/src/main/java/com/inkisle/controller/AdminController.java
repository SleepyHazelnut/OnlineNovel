package com.inkisle.controller;

import com.inkisle.common.CurrentUser;
import com.inkisle.common.Result;
import com.inkisle.dto.LoginDTO;
import com.inkisle.entity.AdminInfo;
import com.inkisle.service.AdminService;
import com.inkisle.util.CaptchaUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端入口。管理员不开放注册，账号直接在数据库中添加，
 * 避免留下注册入口导致自行注册为管理员
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    /**
     * 管理端登录，跟读者端一样先过验证码
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO loginDTO, HttpSession session) {
        CaptchaUtil.verify(session, loginDTO.getCaptcha());
        return adminService.login(loginDTO.getUsername(), loginDTO.getPassword());
    }

    /**
     * 当前登录管理员，adminId 从拦截器塞进 request 的属性里取
     */
    @GetMapping("/info")
    public Result<AdminInfo> info(HttpServletRequest request) {
        Long adminId = CurrentUser.id(request);
        return Result.ok(adminService.getById(adminId));
    }

}
