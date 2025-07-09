package com.inkisle.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 测试用缓存
 * <p>
 * 项目里的缓存管理器依赖 Redis。服务层测试只验证数据库行为，
 * 不应该因为本机没起 Redis 就整套跑不起来，故换成进程内 Map
 */
@TestConfiguration
public class TestCacheConfig {

    /**
     * 方法名不能叫 cacheManager：与 CacheConfig 里的 Bean 同名会触发
     * BeanDefinitionOverrideException，测试上下文直接起不来
     */
    @Bean
    @Primary
    public CacheManager testCacheManager() {
        return new ConcurrentMapCacheManager();
    }

}
