package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.dto.LoginDTO;
import com.inkisle.dto.UserRegisterDTO;
import com.inkisle.entity.UserInfo;
import com.inkisle.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户列表
     */
    @GetMapping("/list")
    public Result<List<UserInfo>> list() {
        return Result.ok(userService.listAll());
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterDTO registerDTO) {
        return userService.register(registerDTO);
    }

    /**
     * 用户登录，返回 token
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO loginDTO) {
        return userService.login(loginDTO.getUsername(), loginDTO.getPassword());
    }

    /**
     * 当前登录用户，userId 由拦截器解析 token 后放进 request
     */
    @GetMapping("/info")
    public Result<UserInfo> info(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(userService.getById(userId));
    }

}
