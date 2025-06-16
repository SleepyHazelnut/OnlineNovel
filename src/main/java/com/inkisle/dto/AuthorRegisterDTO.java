package com.inkisle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者注册入参
 * 仅账号、密码标 @NotBlank；笔名与邮箱可选，故只限制长度、不做非空校验
 */
@Data
public class AuthorRegisterDTO {

    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 20, message = "账号长度要在 4 到 20 之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度要在 6 到 20 之间")
    private String password;

    @Size(max = 20, message = "笔名最多 20 个字")
    private String penName;

    @Size(max = 100, message = "邮箱太长了")
    private String email;

}
