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

/**
 * 读者端账号接口：注册、登录、查看自己的信息
 * 此处仅处理当前用户自身的操作：全量用户列表已移至 `/admin/user`，
 * 普通读者不应有权获取全部用户数据
 */
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
     * 用户登录，成功返回 token
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO loginDTO, HttpSession session) {
        // 先校验验证码再查询账号，避免被字典穷举密码
        CaptchaUtil.verify(session, loginDTO.getCaptcha());
        return userService.login(loginDTO.getUsername(), loginDTO.getPassword());
    }

    /**
     * 当前登录用户，userId 由拦截器解析 token 后写入 request，不接收前端传入
     */
    @GetMapping("/info")
    public Result<UserInfo> info(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(userService.getById(userId));
    }

}
