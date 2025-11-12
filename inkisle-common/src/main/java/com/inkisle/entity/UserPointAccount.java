package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分账户，余额是流水求和出来的物化结果
 * 单独一张表而不是往 user_info 加一列：用户信息有 updateProfile 这种
 * 拿半对象整行覆盖的写法，加进去迟早被静默抹掉
 */
@Data
public class UserPointAccount {

    private Long userId;

    /** 可用余额，等于该用户全部流水的求和 */
    private Integer balance;

    /** 累计获得，只增不减，等级看这个而不是余额 */
    private Integer totalEarned;

    private LocalDateTime updateTime;

}
