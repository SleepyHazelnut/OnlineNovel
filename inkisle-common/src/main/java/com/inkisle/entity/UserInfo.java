package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息
 * 读者账号。密码只存 MD5，不存明文
 */
@Data
public class UserInfo {

    private Long id;

    /** 用户名，唯一 */
    private String username;

    /** 密码，存的是 MD5 摘要，不是明文 */
    private String password;

    /** 昵称 */
    private String nickName;

    private String phone;

    private String email;

    /** 头像地址 */
    private String avatar;

    /** 状态 0正常 1封禁 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
