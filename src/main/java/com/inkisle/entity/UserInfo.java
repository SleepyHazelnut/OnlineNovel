package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息
 */
@Data
public class UserInfo {

    private Long id;

    /** 用户名，唯一 */
    private String username;

    /** 密码，存 MD5 值 */
    private String password;

    /** 昵称 */
    private String nickName;

    private String phone;

    private String email;

    /** 头像地址 */
    private String avatar;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
