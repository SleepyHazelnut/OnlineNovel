package com.inkisle.cache;

import com.inkisle.client.UserClient;
import com.inkisle.common.RedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 账号封禁状态
 *
 * 每个受保护的请求都要问一次「这个账号被封了没」，原先每次都发 Feign 到用户服务查库。
 * 一次翻章下来：网关 → 书籍服务 → 服务间 HTTP → 用户库，多两跳。
 * 状态几乎不变，缓存 30 秒足够：封禁最迟半分钟生效，
 * 而正常请求省掉一次 RPC 和一次查库。
 *
 * 封禁动作在用户服务，它改完直接删这个 key，所以实际生效是即时的，30 秒只是兜底
 */
@Slf4j
@Component
public class UserStatusCache {

    /** 与真实状态区分开：账号不存在也要缓存，否则不存在的 id 会被反复拿来打库 */
    private static final String ABSENT = "null";

    private static final Duration TTL = Duration.ofSeconds(30);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // Feign 客户端和 MVC 配置互相依赖，延迟注入解开
    @Autowired
    @Lazy
    private UserClient userClient;

    /** 测试环境没有 Redis，关掉后直接走 Feign */
    @Value("${inkisle.user-status.cache-enabled:true}")
    private boolean cacheEnabled;

    /** @return 0 正常、1 封禁、null 账号不存在 */
    public Integer get(String role, Long id) {
        String key = RedisKeys.userStatus(role, id);
        if (cacheEnabled) {
            String cached = readCache(key);
            // 缓存里躺着的 null 表示「确认过没有这个账号」，不必再问一次
            if (ABSENT.equals(cached)) {
                return null;
            }
            Integer parsed = parseInt(cached);
            if (parsed != null) {
                return parsed;
            }
            // 解析不出来说明是脏数据，往下走回源，别把正常账号当成不存在
        }

        Integer status = userClient.status(role, id);
        if (cacheEnabled) {
            writeCache(key, status);
        }
        return status;
    }

    private String readCache(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            // 缓存读不了就当作没命中，不能因此拦下正常用户
            return null;
        }
    }

    private Integer parseInt(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            // 脏数据按未命中处理，下一轮会重新回源并覆盖
            return null;
        }
    }

    private void writeCache(String key, Integer status) {
        try {
            stringRedisTemplate.opsForValue()
                    .set(key, status == null ? ABSENT : String.valueOf(status), TTL);
        } catch (Exception e) {
            log.warn("账号状态写入缓存失败 key={}", key, e);
        }
    }

}
