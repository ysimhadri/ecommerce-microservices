package com.ecommerce.cart.service;

import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.model.CartStatus;
import com.ecommerce.cart.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Get-or-create is a converge, not a race-to-fail: every one of N concurrent
 * callers gets the same cart id back and exactly one ACTIVE row exists.
 * Real threads, real Postgres.
 */
@Testcontainers
@SpringBootTest
class ConcurrentCartCreationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    CartService cartService;
    @Autowired
    CartRepository cartRepository;

    @Test
    void concurrentGetOrCreate_allSucceedWithSameCart_oneRow() throws Exception {
        int threads = 8;
        UUID owner = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<CartResponse>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return cartService.getOrCreate(owner);
            }));
        }
        ready.await();
        go.countDown();

        List<UUID> ids = new ArrayList<>();
        for (Future<CartResponse> f : futures) {
            ids.add(f.get(30, TimeUnit.SECONDS).id());
        }
        pool.shutdown();

        assertThat(ids).hasSize(threads).containsOnly(ids.get(0));
        assertThat(cartRepository.countByOwnerIdAndStatus(owner, CartStatus.ACTIVE)).isEqualTo(1);
        assertThat(cartRepository.findAll().stream().filter(c -> c.getOwnerId().equals(owner)).count()).isEqualTo(1);
    }
}
