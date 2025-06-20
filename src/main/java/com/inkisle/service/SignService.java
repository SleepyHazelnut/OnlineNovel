package com.inkisle.service;

import java.util.Map;

/**
 * 签到，记录存储于 Redis 位图
 */
public interface SignService {

    /**
     * 签到，当日已签到则返回 false
     */
    boolean sign(Long userId);

    /**
     * 本月签到情况：已签日期、签到总天数、当日是否已签
     */
    Map<String, Object> monthStatus(Long userId);

    /**
     * 连续签到天数，自今天（或昨天）向前推算
     */
    int continuousDays(Long userId);

}
