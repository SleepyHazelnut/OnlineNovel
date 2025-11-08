package com.inkisle.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 账号封禁状态
 *
 * 每个受保护的请求都要问一次「这个账号被封了没」，原先每次都发 Feign 到用户服务查库。
 * 一次翻章下来：网关 → 书籍服务 → 服务间 HTTP → 用户库，多两跳。
 * 状态几乎不变，缓存 30 秒足够：封禁最迟半分钟生效，
 * 而正常请求省掉一次 RPC 和一次查库。
 *
 * 封禁动作在用户服务，它改完直接删这个 key，所以实际生效是即时的，30 秒只是兜底。
 *
 * 放在 common 而不是某一个服务里，是因为两个服务都要用：
 * 书籍服务回源走 Feign，用户服务回源查自己的库，回源方式由调用方用 loader 传进来，
 * 这样缓存逻辑只有一份，也不会出现「用户服务调自己的 Feign」这种绕圈
 */
@Slf4j
@Component
public class UserStatusCache {

    /** 与真实状态区分开：账号不存在也要缓存，否则不存在的 id 会被反复拿来打库 */
    private static final String ABSENT = "null";

    private static final Duration TTL = Duration.ofSeconds(30);

    /**
     * 用 ObjectProvider 而不是直接注入：审核、统计这类服务没有 Redis，
     * 却和这里在同一个包里被扫描到，硬注入会让它们起不来
     */
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    /** 测试环境没有 Redis，关掉后每次回源 */
    private final boolean cacheEnabled;

    public UserStatusCache(ObjectProvider<StringRedisTemplate> redisProvider,
                           @Value("${inkisle.user-status.cache-enabled:true}") boolean cacheEnabled) {
        this.redisProvider = redisProvider;
        this.cacheEnabled = cacheEnabled;
    }

    private StringRedisTemplate redis() {
        return redisProvider.getIfAvailable();
    }

    private boolean usable() {
        return cacheEnabled && redis() != null;
    }

    /**
     * @param loader 缓存没命中时怎么查真实状态，返回 0 正常、1 封禁、null 账号不存在
     */
    public Integer get(String role, Long id, Supplier<Integer> loader) {
        String key = RedisKeys.userStatus(role, id);
        if (usable()) {
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

        Integer status = loader.get();
        if (usable()) {
            writeCache(key, status);
        }
        return status;
    }

    /** 封禁、解封之后调用：删 key 让下一次请求立刻回源 */
    public void evict(String role, Long id) {
        if (!usable()) {
            return;
        }
        try {
            redis().delete(RedisKeys.userStatus(role, id));
        } catch (Exception e) {
            log.warn("账号状态缓存清理失败 role={} id={}", role, id, e);
        }
    }

    private String readCache(String key) {
        try {
            return redis().opsForValue().get(key);
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
            redis().opsForValue()
                    .set(key, status == null ? ABSENT : String.valueOf(status), TTL);
        } catch (Exception e) {
            log.warn("账号状态写入缓存失败 key={}", key, e);
        }
    }

}
