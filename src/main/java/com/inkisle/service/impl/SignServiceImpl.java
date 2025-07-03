package com.inkisle.service.impl;

import com.inkisle.service.SignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 签到存 Redis 位图，一人一年一张，第 N 天对应第 N 个 bit
 */
@Service
public class SignServiceImpl implements SignService {

    private static final String KEY_PREFIX = "sign:";

    /** 跨年那几天还要读上一年的位图，所以多留一个多月 */
    private static final Duration EXPIRE = Duration.ofDays(400);

    private static final int MAX_CONTINUOUS_DAYS = 366;

    /** 判断与置位放一条脚本里，否则并发请求会都读到未签到，同一天签两次 */
    private static final DefaultRedisScript<Long> SIGN_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GETBIT', KEYS[1], ARGV[1]) == 1 then "
                    + "  return 0 "
                    + "end "
                    + "redis.call('SETBIT', KEYS[1], ARGV[1], 1) "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[2]) "
                    + "return 1",
            Long.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean sign(Long userId) {
        LocalDate today = LocalDate.now();
        String key = buildKey(userId, today);

        Long result = stringRedisTemplate.execute(SIGN_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(offsetOf(today)),
                String.valueOf(EXPIRE.toSeconds()));
        return result != null && result == 1L;
    }

    @Override
    public Map<String, Object> monthStatus(Long userId) {
        LocalDate today = LocalDate.now();
        // 一次取整张位图，逐日 GETBIT 一个月要往返 30 次
        byte[] bitmap = readBitmap(buildKey(userId, today));

        List<Integer> days = new ArrayList<>();
        int lengthOfMonth = today.lengthOfMonth();
        for (int day = 1; day <= lengthOfMonth; day++) {
            if (bitAt(bitmap, offsetOf(today.withDayOfMonth(day)))) {
                days.add(day);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("month", today.getMonthValue());
        data.put("days", days);
        data.put("count", days.size());
        data.put("todaySigned", bitAt(bitmap, offsetOf(today)));
        return data;
    }

    @Override
    public int continuousDays(Long userId) {
        LocalDate today = LocalDate.now();
        return countContinuous(readBitmap(buildKey(userId, today)),
                readBitmap(buildKey(userId, today.minusYears(1))),
                today);
    }

    /** 昨天可能落在去年那张位图上，所以两张一起传进来 */
    static int countContinuous(byte[] thisYear, byte[] lastYear, LocalDate today) {
        LocalDate cursor = bitAt(thisYear, offsetOf(today)) ? today : today.minusDays(1);

        int count = 0;
        while (count < MAX_CONTINUOUS_DAYS) {
            // 跨年只是换一张位图，连续签到本身不该断
            byte[] bitmap = cursor.getYear() == today.getYear() ? thisYear : lastYear;
            if (!bitAt(bitmap, offsetOf(cursor))) {
                break;
            }
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    /** 位图必须按字节读，用 String 取会按 UTF-8 解码，位就废了 */
    private byte[] readBitmap(String key) {
        byte[] raw = stringRedisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
        return raw == null ? new byte[0] : raw;
    }

    private static int offsetOf(LocalDate date) {
        return date.getDayOfYear() - 1;
    }

    /** Redis 的位序从每字节最高位开始，偏移 0 是首字节的 0x80 */
    private static boolean bitAt(byte[] bitmap, int offset) {
        int index = offset >> 3;
        if (index < 0 || index >= bitmap.length) {
            return false;
        }
        return (bitmap[index] & (1 << (7 - (offset & 7)))) != 0;
    }

    private static String buildKey(Long userId, LocalDate date) {
        return KEY_PREFIX + userId + ":" + date.getYear();
    }

}
