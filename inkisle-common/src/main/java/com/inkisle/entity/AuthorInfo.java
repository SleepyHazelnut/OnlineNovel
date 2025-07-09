package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作者信息
 * 与读者分属两套账号，独立注册。资质审核通过前不可发布作品
 */
@Data
public class AuthorInfo {

    private Long id;

    /** 作者账号，唯一 */
    private String username;

    /** 密码，存的是 MD5 摘要 */
    private String password;

    /** 笔名 */
    private String penName;

    private String phone;

    private String email;

    /** 作者简介 */
    private String intro;

    /** 资质审核 0待审 1通过 2驳回，未通过前不可发布作品 */
    private Integer auditStatus;

    /** 资质审核意见 */
    private String auditRemark;

    /** 状态 0正常 1禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
