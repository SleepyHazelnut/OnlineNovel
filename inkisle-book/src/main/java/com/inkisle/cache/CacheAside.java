package com.inkisle.cache;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 读缓存的统一入口
 *
 * 三件事都收在这里：
 * 1. 未命中时只放一个线程回源，其余线程短暂等待后读它的结果，避免热点 key 到期瞬间一起打库
 * 2. 空结果也写缓存：调用方把「没有」表示成空列表或空串传进来，比返回 null 更省事，
 *    Redis 那边配的是禁空值，null 写不进去，正好也不该靠 null 表达业务上的「空」
 * 3. 过期时间在 CacheConfig 的 writer 里加抖动，这一层不用管
 *
 * 没抢到锁的请求不会一直等：等三轮（约 90ms）还没结果就自己查，
 * 多查一次的代价远小于让请求挂在那儿
 */
@Component
public class CacheAside {

    private static final Logger log = LoggerFactory.getLogger(CacheAside.class);

    /** 没抢到锁时每轮等多久、最多等几轮 */
    private static final long WAIT_MILLIS = 30;
    private static final int WAIT_ROUNDS = 3;

    /** 锁的租期：够一次回源，持锁线程崩了也会自动释放，不会把 key 锁死 */
    private static final long LOCK_LEASE_SECONDS = 3;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 先读缓存，没有再回源并写回
     * loader 不要返回 null：空结果请返回空集合或空串，那样才能被缓存住
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String cacheName, String key, Supplier<T> loader) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            // 缓存被关掉时退化成直查，不影响功能
            return loader.get();
        }

        Cache.ValueWrapper cached = cache.get(key);
        if (cached != null) {
            return (T) cached.get();
        }

        RLock lock = redissonClient.getLock("lock:cache:" + cacheName + ":" + key);
        if (!tryLock(lock)) {
            return waitThenLoad(cache, key, loader);
        }
        try {
            // 等锁期间前一个持锁线程可能已经写完，进来再看一眼
            Cache.ValueWrapper again = cache.get(key);
            if (again != null) {
                return (T) again.get();
            }
            T value = loader.get();
            if (value != null) {
                cache.put(key, value);
            }
            return value;
        } finally {
            unlock(lock);
        }
    }

    public void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private <T> T waitThenLoad(Cache cache, String key, Supplier<T> loader) {
        for (int i = 0; i < WAIT_ROUNDS; i++) {
            try {
                Thread.sleep(WAIT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            Cache.ValueWrapper retry = cache.get(key);
            if (retry != null) {
                return cast(retry);
            }
        }
        // 重建迟迟没完成，自己查：多一次查询，好过让请求卡在这里
        return loader.get();
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Cache.ValueWrapper wrapper) {
        return (T) wrapper.get();
    }

    private boolean tryLock(RLock lock) {
        try {
            return lock.tryLock(0, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void unlock(RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            // 租期到了会自动释放，这里失败不影响已经写好的缓存
            log.debug("释放缓存重建锁失败", e);
        }
    }

}
