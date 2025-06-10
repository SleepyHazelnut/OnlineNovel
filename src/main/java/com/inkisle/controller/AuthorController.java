package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.entity.AuthorInfo;
import com.inkisle.service.AuthorService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 作者端入口，注册登录和读者分开，token 还是同一套
 */
@RestController
@RequestMapping("/author")
public class AuthorController {

    @Autowired
    private AuthorService authorService;

    /**
     * 作者注册
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody AuthorInfo authorInfo) {
        return authorService.register(authorInfo);
    }

    /**
     * 作者登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody AuthorInfo authorInfo) {
        return authorService.login(authorInfo.getUsername(), authorInfo.getPassword());
    }

    /**
     * 当前登录作者
     */
    @GetMapping("/info")
    public Result<AuthorInfo> info(HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        return Result.ok(authorService.getById(authorId));
    }

}
