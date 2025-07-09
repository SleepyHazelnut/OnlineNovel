package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员
 * 与用户分表：登录入口、权限均不同，故单独建表
 * 不开放注册，账号由人工直接写入数据库
 */
@Data
public class AdminInfo {

    private Long id;

    /** 管理员账号，唯一索引 */
    private String username;

    /** 密码，存的是 MD5，不存明文 */
    private String password;

    /** 姓名 */
    private String realName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
