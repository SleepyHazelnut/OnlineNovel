package com.inkisle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * 缓存装配
 * <p>
 * 使用 Redis 作为缓存而非本地内存：后续可能多实例部署，本地缓存无法共享
 */
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 默认的 JDK 序列化写入 Redis 是二进制，redis-cli 里看不了，值改用 JSON
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // 书籍信息更新频率低，TTL 设为 30 分钟
                .entryTtl(Duration.ofMinutes(30))
                // 不缓存空值：否则查询不存在的书籍时该结果会被缓存，
                // 书籍上架后仍会命中缓存而读不到
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

}
