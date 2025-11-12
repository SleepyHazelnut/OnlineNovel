package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 读者荐书票，积分唯一的出口
 * 一人一本一票，靠唯一键拦；票投出去不退，所以没有更新路径
 */
@Data
public class UserRecommendVote {

    private Long id;

    private Long userId;

    private Long bookId;

    /** 投票时扣的分，规则调整后老票仍能对上账 */
    private Integer cost;

    private LocalDateTime createTime;

}
