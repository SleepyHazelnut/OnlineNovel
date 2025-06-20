package com.inkisle.service.impl;

import com.inkisle.service.SignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 签到记录存储于 Redis 位图
 * <p>
 * 每个用户每年一张位图，第 N 天对应第 N 个 bit
 * 若按天写入数据库，每个用户每年需 365 行记录；
 * 改用位图后 365 bit 仅约 46 字节，空间占用显著降低
 */
@Service
public class SignServiceImpl implements SignService {

    /** 键长这样：sign:12:2025 */
    private static final String KEY_PREFIX = "sign:";

    /** 按年存储，多留一个多月余量，避免跨年时读取失败 */
    private static final Duration EXPIRE = Duration.ofDays(400);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean sign(Long userId) {
        LocalDate today = LocalDate.now();
        String key = buildKey(userId, today);
        int offset = offsetOf(today);

        // 先检查当天 bit 是否为 1，为 1 表示重复签到
        if (Boolean.TRUE.equals(stringRedisTemplate.opsForValue().getBit(key, offset))) {
            return false;
        }
        stringRedisTemplate.opsForValue().setBit(key, offset, true);
        stringRedisTemplate.expire(key, EXPIRE);
        return true;
    }

    @Override
    public Map<String, Object> monthStatus(Long userId) {
        LocalDate today = LocalDate.now();
        String key = buildKey(userId, today);

        // 位图无法按天批量读取，只能逐日查询本月每一天的 bit
        List<Integer> days = new ArrayList<>();
        int lengthOfMonth = today.lengthOfMonth();
        for (int day = 1; day <= lengthOfMonth; day++) {
            if (Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .getBit(key, offsetOf(today.withDayOfMonth(day))))) {
                days.add(day);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("month", today.getMonthValue());
        data.put("days", days);
        data.put("count", days.size());
        data.put("todaySigned", Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue().getBit(key, offsetOf(today))));
        return data;
    }

    @Override
    public int continuousDays(Long userId) {
        LocalDate today = LocalDate.now();
        String key = buildKey(userId, today);

        // 当天未签到时从昨天开始计算，否则进入页面时连续天数会显示为 0
        LocalDate cursor = Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue().getBit(key, offsetOf(today)))
                ? today : today.minusDays(1);

        int count = 0;
        while (cursor.getYear() == today.getYear()) {
            if (!Boolean.TRUE.equals(stringRedisTemplate.opsForValue().getBit(key, offsetOf(cursor)))) {
                break;
            }
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    /**
     * 第几天转换为位图偏移，偏移从 0 开始，故减 1
     */
    private int offsetOf(LocalDate date) {
        return date.getDayOfYear() - 1;
    }

    private String buildKey(Long userId, LocalDate date) {
        return KEY_PREFIX + userId + ":" + date.getYear();
    }

}
