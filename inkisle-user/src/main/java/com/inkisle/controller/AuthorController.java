package com.inkisle.controller;

import com.inkisle.common.CurrentUser;
import com.inkisle.common.Result;
import com.inkisle.dto.AuthorRegisterDTO;
import com.inkisle.dto.LoginDTO;
import com.inkisle.entity.AuthorInfo;
import com.inkisle.service.AuthorService;
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
 * 作者端入口。注册登录是单独一套，注册还要交资质等审核，
 * 但发出来的 token 跟读者一样，拦截器按路径前缀分角色
 */
@RestController
@RequestMapping("/author")
public class AuthorController {

    @Autowired
    private AuthorService authorService;

    /**
     * 作者注册，比读者多一步资质审核
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody AuthorRegisterDTO registerDTO) {
        return authorService.register(registerDTO);
    }

    /**
     * 作者登录，同样先验验证码
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO loginDTO, HttpSession session) {
        CaptchaUtil.verify(session, loginDTO.getCaptcha());
        return authorService.login(loginDTO.getUsername(), loginDTO.getPassword());
    }

    /**
     * 当前登录作者，authorId 从拦截器的 token 里来
     */
    @GetMapping("/info")
    public Result<AuthorInfo> info(HttpServletRequest request) {
        Long authorId = CurrentUser.id(request);
        return Result.ok(authorService.getById(authorId));
    }

}
