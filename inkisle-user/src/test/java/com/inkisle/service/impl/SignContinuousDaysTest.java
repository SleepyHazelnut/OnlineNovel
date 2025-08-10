package com.inkisle.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 连续签到天数，纯函数，不用 Redis
 * 单独拎出来测就是因为跨年：1 月 1 日的时候昨天在去年那张位图上，
 * 早先拿「年份相同」当循环条件，一到元旦连续天数就成了 0
 * 这种日期边界跑接口测不出来，总不能等一年
 */
class SignContinuousDaysTest {

    /** 一年最多 366 天，位图按字节算需要 46 字节 */
    private static final int BITMAP_BYTES = 46;

    @Test
    @DisplayName("跨年不断签：1 月 1 日能接上去年 12 月 31 日")
    void continuousAcrossNewYear() {
        LocalDate today = LocalDate.of(2026, 1, 1);
        // 今年只有 1 月 1 日；去年 12 月 30、31 两天
        byte[] thisYear = bitmap(1);
        byte[] lastYear = bitmap(365, 364);

        assertThat(SignServiceImpl.countContinuous(thisYear, lastYear, today)).isEqualTo(3);
    }

    @Test
    @DisplayName("今天还没签，从昨天开始算")
    void countsFromYesterdayWhenTodayMissing() {
        LocalDate today = LocalDate.of(2026, 1, 1);

        assertThat(SignServiceImpl.countContinuous(bitmap(), bitmap(365, 364), today)).isEqualTo(2);
    }

    @Test
    @DisplayName("中间断一天就停在那天")
    void stopsAtGap() {
        LocalDate today = LocalDate.of(2026, 10, 17);
        // 17（第 290 天）、16 有，15 没有，14 有也不该跨过空档算进去
        byte[] thisYear = bitmap(290, 289, 287);

        assertThat(SignServiceImpl.countContinuous(thisYear, bitmap(), today)).isEqualTo(2);
    }

    @Test
    @DisplayName("没有任何记录时返回 0")
    void emptyBitmapGivesZero() {
        LocalDate today = LocalDate.of(2026, 6, 1);

        assertThat(SignServiceImpl.countContinuous(bitmap(), bitmap(), today)).isZero();
    }

    /**
     * 按「一年的第几天」构造位图，位序与 Redis SETBIT 一致：
     * 偏移 0 是首字节的最高位
     */
    private byte[] bitmap(int... daysOfYear) {
        byte[] bytes = new byte[BITMAP_BYTES];
        for (int day : daysOfYear) {
            int offset = day - 1;
            bytes[offset >> 3] |= (byte) (1 << (7 - (offset & 7)));
        }
        return bytes;
    }

}
