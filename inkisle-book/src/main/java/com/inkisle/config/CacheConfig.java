package com.inkisle.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.inkisle.cache.CacheNames;
import com.inkisle.cache.JitterRedisCacheWriter;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存配置。没用本地缓存，后面拆多实例时共享不了
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 书籍详情：上架状态、字数、评分都在变，30 分钟兜底 */
    private static final Duration BOOK_TTL = Duration.ofMinutes(30);

    /** 目录：只有章节增删和审核状态会动它 */
    private static final Duration CATALOG_TTL = Duration.ofMinutes(30);

    /**
     * 正文：整条阅读链路上最大的一条数据，写下去之后基本不再变，
     * 只有作者改稿才会动，那时会主动删 key，所以 TTL 给长一点
     */
    private static final Duration CONTENT_TTL = Duration.ofHours(2);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // JDK 序列化存进去是二进制，redis-cli 里没法看，换成 JSON
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 反序列化需还原为原类型，必须带类型信息，但只允许这几个包
        // java.math 别漏，BookInfo.score 是 BigDecimal，没放行的话写得进去读不出来
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.inkisle.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.math.")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                // 空值不缓存，否则未上架的书会被缓存为不存在，上架后仍读不到。
                // 业务上真要表达「没有」，用空集合或空串，别返回 null
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));

        // 三个区域的变化频率差得远，TTL 分开设，不共用一个默认值
        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put(CacheNames.BOOK, base.entryTtl(BOOK_TTL));
        perCache.put(CacheNames.CHAPTER_CATALOG, base.entryTtl(CATALOG_TTL));
        perCache.put(CacheNames.CHAPTER_CONTENT, base.entryTtl(CONTENT_TTL));

        // writer 里给每个 key 的过期时间加了随机量，同一批数据不会同一秒一起失效
        return RedisCacheManager.builder(new JitterRedisCacheWriter(connectionFactory))
                .cacheDefaults(base.entryTtl(BOOK_TTL))
                .withInitialCacheConfigurations(perCache)
                .build();
    }

}
