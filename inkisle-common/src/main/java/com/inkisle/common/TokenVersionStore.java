package com.inkisle.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 身份版本号，用来让 token 提前失效
 *
 * JWT 是无状态的，签出去之后服务端说了不算：登出、改密码、被封禁、被降权，
 * 旧 token 照样能用满 24 小时。最直接的办法是黑名单，但要按 token 存、还要跟着过期时间清理，
 * 存储量随登录次数增长。这里改成「版本号」——
 * 每个账号在 Redis 里有一个整数，签发时写进 claim，校验时比对；
 * 改密码、踢下线就把这个整数加一，之前签出去的 token 全部对不上，一次写入清掉所有会话。
 */
@Slf4j
@Component
public class TokenVersionStore {

    /**
     * 用 ObjectProvider 而不是直接注入：审核、统计这类服务不需要这套，
     * 它们和这里在同一个包里被扫描到，取不到模板时按「未启用」处理
     */
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    /** 测试环境没有 Redis，关掉后不做版本校验 */
    private final boolean enabled;

    public TokenVersionStore(ObjectProvider<StringRedisTemplate> redisProvider,
                             @Value("${inkisle.token-version.enabled:true}") boolean enabled) {
        this.redisProvider = redisProvider;
        this.enabled = enabled;
    }

    private StringRedisTemplate redis() {
        return redisProvider.getIfAvailable();
    }

    /** 当前版本，没设置过就是 0 */
    public String current(String role, Long id) {
        StringRedisTemplate template = redis();
        if (!enabled || template == null) {
            return "0";
        }
        try {
            String value = template.opsForValue().get(RedisKeys.tokenVersion(role, id));
            return value == null ? "0" : value;
        } catch (Exception e) {
            // Redis 读不到时按 0 处理，与未设置过一致；不能因为缓存故障把人挡在门外
            log.warn("读取身份版本失败 role={} id={}", role, id, e);
            return "0";
        }
    }

    /** 让该账号已签发的 token 全部失效：登出、改密、封禁时调用 */
    public void bump(String role, Long id) {
        StringRedisTemplate template = redis();
        if (!enabled || template == null) {
            return;
        }
        try {
            template.opsForValue().increment(RedisKeys.tokenVersion(role, id));
        } catch (Exception e) {
            log.warn("身份版本自增失败 role={} id={}", role, id, e);
        }
    }

    /** token 里的版本号与当前版本不一致即为已失效。老版本 token 没有该 claim，按 0 处理 */
    public boolean matches(String role, Long id, String version) {
        if (!enabled || redis() == null) {
            return true;
        }
        String expected = current(role, id);
        return expected.equals(version == null ? "0" : version);
    }

}
