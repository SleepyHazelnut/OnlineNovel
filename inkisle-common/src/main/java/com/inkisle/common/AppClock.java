package com.inkisle.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 业务时区
 *
 * 签到、积分每日上限这类规则全是「按天」的，而 LocalDate.now() 取的是 JVM 默认时区。
 * 容器默认是 UTC，改一次 TZ 或者换台机器，北京时间 00:00 和 08:01 会落进两个不同的「今天」，
 * 等于一天能签两次、能拿两份连续奖励。
 *
 * 时区写死成配置项而不是跟着系统走：这类规则的正确性不该取决于部署环境
 */
@Component
public class AppClock {

    private final ZoneId zone;

    public AppClock(@Value("${inkisle.zone:Asia/Shanghai}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(zone);
    }

    /** 当天零点，用于「今天的分」这类区间统计 */
    public LocalDateTime startOfToday() {
        return today().atStartOfDay();
    }

}
