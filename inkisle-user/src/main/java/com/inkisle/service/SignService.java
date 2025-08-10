package com.inkisle.service;

import java.util.Map;

/**
 * 签到，记录存在 Redis 位图里
 */
public interface SignService {

    /**
     * 签到，今天签过了返回 false
     */
    boolean sign(Long userId);

    /**
     * 本月签到情况，已签的日期、总天数、今天签没签
     */
    Map<String, Object> monthStatus(Long userId);

    /**
     * 连续签到天数，从今天（或者昨天）往前推
     */
    int continuousDays(Long userId);

}
