package com.inkisle.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 缓存失效的统一入口
 *
 * 写库之后立刻删缓存，看着没问题，实际漏了「事务提交」这一步：
 * 删 key 的时候事务还没提交，这时候进来的读请求读到的仍是提交前的旧行，
 * 它会把这个旧值重新写进缓存，等事务提交后缓存反而脏了，一直脏到 TTL。
 * 正文的 TTL 是两小时，这种窗口不该存在。
 *
 * 事务内的调用改成注册到 afterCommit：提交后才有别人能读到新值，此时再删，
 * 下次读必然回源拿到新数据。没有事务时（比如导入的最后一步）直接删。
 */
@Component
public class CacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidator.class);

    @Autowired
    private CacheAside cacheAside;

    public void evict(String cacheName, Object key) {
        // 先删一次：事务内后续的读立刻能看到失效，也顺手清掉写之前就已过期的旧值
        safeEvict(cacheName, key);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        // 提交后再删一次。删完到提交之间进来的读请求读到的还是旧行，
        // 它会把这个旧值写回缓存，第二刀就是补这个窗口
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeEvict(cacheName, key);
            }
        });
    }

    /**
     * Redis 挂了不该让已经提交的写操作报错：缓存过期后自然一致，
     * 把异常抛回去只会让用户以为保存失败，然后重复提交一次
     */
    private void safeEvict(String cacheName, Object key) {
        try {
            cacheAside.evict(cacheName, key);
        } catch (RuntimeException e) {
            log.warn("删除缓存失败，等 TTL 自然过期 cache={} key={}", cacheName, key, e);
        }
    }

}
