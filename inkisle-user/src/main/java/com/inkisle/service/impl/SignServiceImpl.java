package com.inkisle.service.impl;

import com.inkisle.common.AppClock;
import com.inkisle.common.PointBizType;
import com.inkisle.dto.SignResult;
import com.inkisle.mapper.UserSignMapper;
import com.inkisle.service.PointService;
import com.inkisle.service.SignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.types.Expiration;
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

    /**
     * 库里确实没有签到记录时，往 Redis 写一个 0 字节占位。
     * 它本身就是「查过了，这一年没有记录」的负缓存，key 在就不必再问库
     */
    private static final byte[] EMPTY_BITMAP = new byte[]{0};

    /** 跨年那几天还要读上一年的位图，所以多留一个多月 */
    private static final Duration EXPIRE = Duration.ofDays(400);

    private static final int MAX_CONTINUOUS_DAYS = 366;

    /** 连签里程碑：签满这些天当天额外给分，只给一次，不重复给 */
    private static final int[] STREAK_MILESTONES = {3, 7, 30};

    /** 与里程碑一一对应 */
    private static final int[] STREAK_BONUS = {3, 8, 20};

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

    @Autowired
    private PointService pointService;

    /**
     * 签到日期一律取业务时区，不用 LocalDate.now()：
     * 容器默认 UTC，改一次 TZ 就能在北京时间 00:00 和 08:01 各签一次，
     * 一天拿两天的分，连签天数也跟着虚高
     */
    @Autowired
    private AppClock clock;

    @Override
    public SignResult sign(Long userId) {
        LocalDate today = clock.today();
        String key = buildKey(userId, today);

        // 先把位图准备好再去置位。反过来的话，位图不在时 Lua 的 SETBIT 会新建一个
        // 「只有今天一个 bit」的 key，之后 readBitmap 认为有数据、不再回库，
        // 这一年的历史签到就永久读不出来了
        readBitmap(userId, today.getYear());

        Long result = stringRedisTemplate.execute(SIGN_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(offsetOf(today)),
                String.valueOf(EXPIRE.toSeconds()));
        boolean signed = result != null && result == 1L;
        if (!signed) {
            return SignResult.repeated();
        }
        persist(userId, today);

        int points = 0;
        // 基础分和连签奖励分成两条流水：明细里才看得出「多出来的 8 分是哪来的」
        if (pointService.award(userId, PointBizType.SIGN.getCode(), today.toEpochDay(), null, "每日签到")) {
            points += PointBizType.SIGN.getPoints();
        }
        int continuous = continuousDays(userId);
        int bonus = streakBonus(continuous);
        if (bonus > 0
                && pointService.award(userId, PointBizType.SIGN_STREAK.getCode(),
                        today.toEpochDay(), bonus, "连签 " + continuous + " 天")) {
            points += bonus;
        }
        return SignResult.signed(points);
    }

    /** 只有刚好签满 3/7/30 天那次才给，第 4 天不给、第 8 天也不重复给第 7 天的 */
    static int streakBonus(int continuousDays) {
        for (int i = 0; i < STREAK_MILESTONES.length; i++) {
            if (continuousDays == STREAK_MILESTONES[i]) {
                return STREAK_BONUS[i];
            }
        }
        return 0;
    }

    /**
     * 位图是加速与去重，库才是记录
     * 写库失败不能把签到判成失败：Redis 那边已经置位了，用户再点会说「今天签过了」，
     * 这时候返回失败反而让他以为没签上
     */
    private void persist(Long userId, LocalDate date) {
        try {
            userSignMapper.insertIgnore(userId, date, clock.now());
        } catch (Exception e) {
            log.warn("签到落库失败，位图已写入 userId={} date={}", userId, date, e);
        }
    }

    @Override
    public Map<String, Object> monthStatus(Long userId) {
        LocalDate today = clock.today();
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
        LocalDate today = clock.today();
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
     * 两种都只能问库。
     *
     * 库里没有记录时不能只是「什么都不写」：那样每次读都要再问一次库，
     * 而新用户恰恰是最常打开签到页的那批人。写一个 0 字节占位当负缓存，
     * key 存在就说明「对过了，这一年没有记录」，读的时候走 readBitmap 的提前返回
     */
    private byte[] rebuildFromDb(Long userId, int year) {
        String key = KEY_PREFIX + userId + ":" + year;
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        List<LocalDate> dates = userSignMapper.selectDates(userId,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        if (dates.isEmpty()) {
            stringRedisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.stringCommands().set(keyBytes, EMPTY_BITMAP,
                        Expiration.seconds(EXPIRE.toSeconds()), RedisStringCommands.SetOption.upsert());
                return null;
            });
            return EMPTY_BITMAP;
        }

        // 一天一次 SETBIT 要跑几百个往返，攒成一批发出去
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (LocalDate date : dates) {
                connection.stringCommands().setBit(keyBytes, offsetOf(date), true);
            }
            return null;
        });
        stringRedisTemplate.expire(key, EXPIRE);
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
