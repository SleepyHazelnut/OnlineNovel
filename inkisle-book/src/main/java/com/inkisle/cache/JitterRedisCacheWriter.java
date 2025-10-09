package com.inkisle.cache;

import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 给每个 key 的过期时间加一点随机量，避免同一批缓存同一秒失效、请求一起回源
 * 只包一层，读写全交给默认实现，只有写入路径的 ttl 被改过
 * 幅度取 15%：30 分钟的缓存落在 30 到 34.5 分钟之间，
 * 抖动太大等于白设 TTL，太小又打不散
 */
public class JitterRedisCacheWriter implements RedisCacheWriter {

    private static final double JITTER_RATIO = 0.15;

    private final RedisCacheWriter delegate;

    public JitterRedisCacheWriter(RedisConnectionFactory connectionFactory) {
        this(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory));
    }

    private JitterRedisCacheWriter(RedisCacheWriter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void put(String name, byte[] key, byte[] value, Duration ttl) {
        delegate.put(name, key, value, jitter(ttl));
    }

    @Override
    public CompletableFuture<Void> store(String name, byte[] key, byte[] value, Duration ttl) {
        return delegate.store(name, key, value, jitter(ttl));
    }

    @Override
    public byte[] putIfAbsent(String name, byte[] key, byte[] value, Duration ttl) {
        return delegate.putIfAbsent(name, key, value, jitter(ttl));
    }

    @Override
    public byte[] get(String name, byte[] key) {
        return delegate.get(name, key);
    }

    @Override
    public CompletableFuture<byte[]> retrieve(String name, byte[] key, Duration ttl) {
        return delegate.retrieve(name, key, ttl);
    }

    @Override
    public void remove(String name, byte[] key) {
        delegate.remove(name, key);
    }

    @Override
    public void clean(String name, byte[] pattern) {
        delegate.clean(name, pattern);
    }

    @Override
    public void clearStatistics(String name) {
        delegate.clearStatistics(name);
    }

    @Override
    public CacheStatistics getCacheStatistics(String cacheName) {
        return delegate.getCacheStatistics(cacheName);
    }

    /** 开了统计也不能把抖动那一层丢掉，所以包着返回而不是直接给原对象 */
    @Override
    public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector collector) {
        return new JitterRedisCacheWriter(delegate.withStatisticsCollector(collector));
    }

    /** 不过期（ttl 为空或零）时不加抖动，那种 key 本来就要一直留着 */
    private static Duration jitter(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return ttl;
        }
        long extra = (long) (ttl.toMillis() * JITTER_RATIO * ThreadLocalRandom.current().nextDouble());
        return ttl.plusMillis(extra);
    }

}
