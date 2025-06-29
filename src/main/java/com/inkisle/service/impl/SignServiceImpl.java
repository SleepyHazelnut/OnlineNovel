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

    /** 连续天数的计算上限，一年零一天；再往前追也没有实际意义 */
    private static final int MAX_CONTINUOUS_DAYS = 366;

    /**
     * 判断与置位放在同一条脚本里执行
     * 拆成 GETBIT + SETBIT 两条命令时，两个并发请求可能都读到 0，
     * 于是同一天被记成「签到成功」两次，连续天数也会跟着算错
     */
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
        // 整张位图一次取回，再在内存里逐日取位；逐日 GETBIT 一个月要往返 28–31 次
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

    /**
     * 从两张位图里数出连续签到天数
     * <p>
     * 拆出来是为了能直接构造位图测试跨年：今天是 1 月 1 日时，昨天落在去年那张位图上，
     * 早先的实现拿 {@code cursor.getYear() == today.getYear()} 当循环条件，
     * 一跨年条件立刻不成立，连续天数直接返回 0
     *
     * @param thisYear 今年的位图
     * @param lastYear 去年的位图
     * @param today    今天
     * @return 连续签到天数，最多一年零一天
     */
    static int countContinuous(byte[] thisYear, byte[] lastYear, LocalDate today) {
        // 当天未签到时从昨天开始算，否则进入页面时连续天数会显示为 0
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
     * 取整张位图的原始字节。位图不是文本，必须走二进制命令读，
     * 用 String 读取会按 UTF-8 解码，字节被改写后就再也解析不出位
     */
    private byte[] readBitmap(String key) {
        byte[] raw = stringRedisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
        return raw == null ? new byte[0] : raw;
    }

    /**
     * 第几天转换为位图偏移，偏移从 0 开始，故减 1
     */
    private static int offsetOf(LocalDate date) {
        return date.getDayOfYear() - 1;
    }

    /**
     * 取指定位。Redis 的位序是每字节从最高位开始：偏移 0 对应首字节的 0x80
     */
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
