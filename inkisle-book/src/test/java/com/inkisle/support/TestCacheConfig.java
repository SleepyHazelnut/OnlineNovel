package com.inkisle.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 测试用的缓存
 * 正式那套缓存管理器要连 Redis。服务层测试只是验数据库行为，
 * 不该因为本机没开 Redis 就跑不起来，所以换成进程内的 Map
 */
@TestConfiguration
public class TestCacheConfig {

    /**
     * 方法名别叫 cacheManager，跟 CacheConfig 里的 Bean 重名会报
     * BeanDefinitionOverrideException，测试上下文直接起不来
     */
    @Bean
    @Primary
    public CacheManager testCacheManager() {
        return new ConcurrentMapCacheManager();
    }

}
