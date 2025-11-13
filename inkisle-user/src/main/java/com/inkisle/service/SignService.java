package com.inkisle.service;

import com.inkisle.dto.SignResult;

import java.util.Map;

/**
 * 签到，记录存在 Redis 位图里
 */
public interface SignService {

    /**
     * 签到。返回本次拿到的积分，「今天已经签过」与「签上了但没给分」靠 repeat 区分
     */
    SignResult sign(Long userId);

    /**
     * 本月签到情况，已签的日期、总天数、今天签没签
     */
    Map<String, Object> monthStatus(Long userId);

    /**
     * 连续签到天数，从今天（或者昨天）往前推
     */
    int continuousDays(Long userId);

}
