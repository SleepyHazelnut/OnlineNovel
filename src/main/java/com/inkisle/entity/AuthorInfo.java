package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作者信息
 */
@Data
public class AuthorInfo {

    private Long id;

    /** 作者账号，唯一 */
    private String username;

    /** 密码，存 MD5 值 */
    private String password;

    /** 笔名 */
    private String penName;

    private String phone;

    private String email;

    /** 作者简介 */
    private String intro;

    /** 状态 0正常 1禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
