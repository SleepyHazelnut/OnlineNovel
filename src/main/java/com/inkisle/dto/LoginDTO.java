package com.inkisle.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录入参
 * 用户名、密码、验证码均为必填。验证码用于拦截脚本撞库
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码必填；@NotBlank 同时拦截空串与纯空格，校验在进入 Controller 前执行 */
    @NotBlank(message = "验证码不能为空")
    private String captcha;

}
