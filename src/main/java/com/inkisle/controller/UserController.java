package com.inkisle.controller;

import com.inkisle.common.CurrentUser;
import com.inkisle.common.Result;
import com.inkisle.dto.LoginDTO;
import com.inkisle.dto.PasswordDTO;
import com.inkisle.dto.UserProfileDTO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 读者端账号接口，只处理当前用户自己的信息 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterDTO registerDTO) {
        return userService.register(registerDTO);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO loginDTO, HttpSession session) {
        // 先校验验证码再查账号，否则可以拿库里的账号去试密码
        CaptchaUtil.verify(session, loginDTO.getCaptcha());
        return userService.login(loginDTO.getUsername(), loginDTO.getPassword());
    }

    @GetMapping("/info")
    public Result<UserInfo> info(HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        return Result.ok(userService.getById(userId));
    }

    @PutMapping("/profile")
    public Result<UserInfo> updateProfile(@Valid @RequestBody UserProfileDTO profileDTO,
                                          HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        return Result.ok(userService.updateProfile(userId, profileDTO));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordDTO passwordDTO,
                                       HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        userService.changePassword(userId, passwordDTO);
        return Result.ok();
    }

}
