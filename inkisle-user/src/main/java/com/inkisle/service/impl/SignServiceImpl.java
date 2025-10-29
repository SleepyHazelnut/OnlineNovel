package com.inkisle.service.impl;

import com.inkisle.mapper.UserSignMapper;
import com.inkisle.service.SignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 签到存 Redis 位图，一人一年一张，第 N 天就是第 N 个 bit
 *
 * 位图不是账本：Redis 清了或换了实例，签到记录不能跟着消失。
 * 所以每次签到的结果同时往 user_sign 写一行；读的时候发现位图不在，
 * 就按年从库里重建一张再接着读
 */
@Slf4j
@Service
public class SignServiceImpl implements SignService {

    private static final String KEY_PREFIX = "sign:";

    /** 某个用户某年的位图已经跟库对过一次，避免每个新用户每次读都查一遍库 */
    private static final String READY_PREFIX = "sign:ready:";

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

    @Autowired
    private UserSignMapper userSignMapper;

    @Override
    public boolean sign(Long userId) {
        LocalDate today = LocalDate.now();
        String key = buildKey(userId, today);

        Long result = stringRedisTemplate.execute(SIGN_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(offsetOf(today)),
                String.valueOf(EXPIRE.toSeconds()));
        boolean signed = result != null && result == 1L;
        if (signed) {
            persist(userId, today);
        }
        return signed;
    }

    /**
     * 位图是加速与去重，库才是记录
     * 写库失败不能把签到判成失败：Redis 那边已经置位了，用户再点会说「今天签过了」，
     * 这时候返回失败反而让他以为没签上
     */
    private void persist(Long userId, LocalDate date) {
        try {
            userSignMapper.insertIgnore(userId, date, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("签到落库失败，位图已写入 userId={} date={}", userId, date, e);
        }
    }

    @Override
    public Map<String, Object> monthStatus(Long userId) {
        LocalDate today = LocalDate.now();
        // 整张位图一次取回来，一天一个 GETBIT 一个月得跑 30 趟
        byte[] bitmap = readBitmap(userId, today.getYear());

        List<Integer> days = new ArrayList<>();
        int lengthOfMonth = today.lengthOfMonth();
        for (int day = 1; day <= lengthOfMonth; day++) {
            if (bitAt(bitmap, offsetOf(today.withDayOfMonth(day)))) {
                days.add(day);
            }
        }

        Map<String, Object> data = new HashMap<>();
        // 年月一并返回：前端只认自己的本地日期时，跨年零点前后会和服务端差一天
        data.put("year", today.getYear());
        data.put("month", today.getMonthValue());
        data.put("days", days);
        data.put("count", days.size());
        data.put("todaySigned", bitAt(bitmap, offsetOf(today)));
        return data;
    }

    @Override
    public int continuousDays(Long userId) {
        LocalDate today = LocalDate.now();
        return countContinuous(readBitmap(userId, today.getYear()),
                readBitmap(userId, today.minusYears(1).getYear()),
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

    /**
     * 位图必须按字节读，用 String 取会按 UTF-8 解码，位就废了
     * 读不到就试着按年从库里重建，重建的是「缓存」，不是自己造记录
     */
    private byte[] readBitmap(Long userId, int year) {
        byte[] raw = readRaw(KEY_PREFIX + userId + ":" + year);
        if (raw.length > 0) {
            return raw;
        }
        return rebuildFromDb(userId, year);
    }

    private byte[] readRaw(String key) {
        byte[] raw = stringRedisTemplate.execute((RedisCallback<byte[]>)
                connection -> connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
        return raw == null ? new byte[0] : raw;
    }

    /**
     * Redis 里没有这张位图：可能是新用户，也可能是缓存被清过或换了实例。
     * 两种都只能问库 —— 但新用户会一直问，所以对过一次就打个标记，一年内不再查
     */
    private byte[] rebuildFromDb(Long userId, int year) {
        String readyKey = READY_PREFIX + userId + ":" + year;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(readyKey))) {
            return new byte[0];
        }

        List<LocalDate> dates = userSignMapper.selectDates(userId,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        if (dates.isEmpty()) {
            stringRedisTemplate.opsForValue().set(readyKey, "1", EXPIRE);
            return new byte[0];
        }

        String key = KEY_PREFIX + userId + ":" + year;
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        // 一天一次 SETBIT 要跑几百个往返，攒成一批发出去
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (LocalDate date : dates) {
                connection.stringCommands().setBit(keyBytes, offsetOf(date), true);
            }
            return null;
        });
        stringRedisTemplate.expire(key, EXPIRE);
        stringRedisTemplate.opsForValue().set(readyKey, "1", EXPIRE);
        log.info("签到位图已从库里重建 userId={} year={} 共 {} 天", userId, year, dates.size());
        return readRaw(key);
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
