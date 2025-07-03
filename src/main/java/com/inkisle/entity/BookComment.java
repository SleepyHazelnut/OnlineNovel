package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 书籍评论
 * 仅两级：一级评论与回复。回复的 parentId 指向一级评论 id，一级评论的 parentId 为 0
 */
@Data
public class BookComment {

    private Long id;

    private Long bookId;

    private Long userId;

    private String content;

    /** 父评论ID，0 表示一级评论 */
    private Long parentId;

    /** 审核状态，取值见 ContentAuditStatus：0 待审 1 通过 2 驳回 */
    private Integer auditStatus;

    /** 驳回原因，只给作者本人与管理端看 */
    private String rejectReason;

    private LocalDateTime createTime;

    /** 评论人昵称，联表带出来 */
    private String nickName;

    /** 书名，管理端与「我的评论」列表联表带出 */
    private String bookName;

    /** 回复列表，只有一级评论会带上 */
    private List<BookComment> children;

}
