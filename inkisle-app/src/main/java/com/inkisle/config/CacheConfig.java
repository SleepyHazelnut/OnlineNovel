package com.inkisle.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
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
        // 默认序列化器是 JDK 序列化，存入 Redis 为二进制，redis-cli 中不可读，换成 JSON
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 为保证取出后能还原原类型，需开启默认类型信息；
        // 但仅白名单放开本项目、集合与数值类型，避免反序列化被利用
        // java.math 不能漏：BookInfo.score 是 BigDecimal，非 final 类型会带类型标记，
        // 未放行时写入正常、回读抛 InvalidTypeIdException
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.inkisle.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.math.")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

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
