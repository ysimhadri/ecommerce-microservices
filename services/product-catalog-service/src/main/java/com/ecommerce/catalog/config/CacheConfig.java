package com.ecommerce.catalog.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * CQRS-lite split, made explicit: {@code *CommandService} classes own every
 * write and never read from a cache; {@code *QueryService} classes are the
 * read-optimized side and cache their hottest lookups (the category list,
 * single-product-by-id) behind Caffeine - an in-process cache with no extra
 * infrastructure to run for a v1 service. A write evicts the caches it can
 * invalidate (see {@code CategoryCommandService}), so a read is never more
 * than one cache-refill behind the last write.
 *
 * Cache names, size bound and TTL live in {@code application.yml}
 * ({@code spring.cache.*}) so they can be tuned per environment without a
 * rebuild; this class only turns the {@code @Cacheable}/{@code @CacheEvict}
 * annotations on.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
