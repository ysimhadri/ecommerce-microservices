package com.ecommerce.eligibility.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Custom health: Redis ping (when a connection factory exists) plus the
 * credit-bureau circuit-breaker state.
 */
@Component
public class EligibilityInfrastructureHealthIndicator implements HealthIndicator {

    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactory;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public EligibilityInfrastructureHealthIndicator(
            ObjectProvider<RedisConnectionFactory> redisConnectionFactory,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        RedisConnectionFactory redis = redisConnectionFactory.getIfAvailable();
        if (redis == null) {
            builder.withDetail("redis", "DISABLED");
        } else {
            try (RedisConnection connection = redis.getConnection()) {
                connection.ping();
                builder.withDetail("redis", "UP");
            } catch (RuntimeException ex) {
                builder.down().withDetail("redis", "DOWN").withDetail("redisError", ex.getMessage());
            }
        }

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("creditBureau");
        CircuitBreaker.State state = breaker.getState();
        builder.withDetail("creditBureauCircuitBreaker", state.name());
        builder.withDetail("creditBureauFailureRate", breaker.getMetrics().getFailureRate());
        if (state == CircuitBreaker.State.OPEN) {
            builder.status("DEGRADED");
        }

        return builder.build();
    }
}
