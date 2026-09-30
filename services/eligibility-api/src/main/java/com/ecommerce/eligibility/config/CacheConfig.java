package com.ecommerce.eligibility.config;

import com.ecommerce.eligibility.dto.EligibilityResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Spring Cache + Redis: eligibility results keyed by customerId+productType
 * with a 10-minute TTL. Absent a {@link RedisConnectionFactory} (test/local
 * simple-cache profiles) this bean is skipped and Spring Boot's configured
 * cache type takes over.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${app.cache.eligibility-ttl:10m}") Duration eligibilityTtl) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        Jackson2JsonRedisSerializer<EligibilityResponse> valueSerializer =
                new Jackson2JsonRedisSerializer<>(mapper, EligibilityResponse.class);

        RedisCacheConfiguration eligibility = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(eligibilityTtl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(eligibility)
                .withInitialCacheConfigurations(Map.of("eligibility", eligibility))
                .build();
    }
}
