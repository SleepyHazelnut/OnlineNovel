package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.dto.LoginDTO;
import com.inkisle.dto.UserRegisterDTO;
import com.inkisle.entity.UserInfo;
import com.inkisle.service.UserService;
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

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册，参数校验交给 DTO 上的注解加 `@Valid`
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterDTO registerDTO) {
        return userService.register(registerDTO);
    }

    /**
     * 用户登录，返回 token
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO loginDTO, HttpSession session) {
        // 先校验验证码再查询账号，避免被字典穷举密码
        CaptchaUtil.verify(session, loginDTO.getCaptcha());
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
