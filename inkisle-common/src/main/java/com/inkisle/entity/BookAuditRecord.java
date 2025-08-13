package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书籍审核记录
 * 作者每次提交审核 insert 一条记录，管理员只更新该条状态，历史记录不删除
 * 判断书过没过审得看最新那条记录，只看有没有记录是不对的
 */
@Data
public class BookAuditRecord {

    private Long id;

    /** 书籍ID */
    private Long bookId;

    /** 提交的作者ID */
    private Long authorId;

    /** 审核状态 0待审 1通过 2驳回 */
    private Integer status;

    /** 审核意见，驳回时必填 */
    private String remark;

    /** 审核人ID */
    private Long auditAdminId;

    private LocalDateTime submitTime;

    private LocalDateTime auditTime;

    /** 联表带出来的书名，不是本表的字段 */
    private String bookName;

}
