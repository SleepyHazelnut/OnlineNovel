package com.inkisle.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料
 * 手机号与邮箱允许留空，留空表示不填。头像只接受读取到的地址，
 * 前端先调上传接口拿到 URL 再提交，不在这个接口里传文件
 */
@Data
public class UserProfileDTO {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称最多 50 个字")
    private String nickName;

    /** 空串放行，非空时按 11 位手机号校验 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不对")
    private String phone;

    @Email(message = "邮箱格式不对")
    @Size(max = 100, message = "邮箱最多 100 个字符")
    private String email;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;

}
