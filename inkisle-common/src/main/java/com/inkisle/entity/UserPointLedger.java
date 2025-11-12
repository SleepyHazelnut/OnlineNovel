package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水，只增不改
 * (user_id, biz_type, biz_id) 唯一，一条业务只记一次账
 */
@Data
public class UserPointLedger {

    private Long id;

    private Long userId;

    /** 来源类型，见 PointBizType */
    private String bizType;

    /** 来源业务 ID：章节取 chapterId，评论取 commentId，评分取 bookId */
    private Long bizId;

    /** 正数为赚，负数为花 */
    private Integer points;

    private String remark;

    private LocalDateTime createTime;

}
