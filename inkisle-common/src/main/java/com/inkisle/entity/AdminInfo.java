package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员
 * 跟读者分开一张表，登录入口和权限都不一样
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
