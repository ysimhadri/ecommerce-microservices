package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.exception.DuplicateEmailException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the register-path concurrency race: {@code User.id} is a
 * client-assigned UUID, so without {@link com.ecommerce.auth.model.User} implementing
 * {@code Persistable} (forcing {@code persist()} over {@code merge()}), a concurrent
 * duplicate-email {@code save()} defers its INSERT to flush/commit time - after
 * {@link AuthService#register}'s try/catch has already returned - so the unique-constraint
 * violation escapes uncaught instead of becoming the intended {@link DuplicateEmailException}.
 * Real threads, real Postgres (Testcontainers) - a mocked repository can't reproduce this,
 * since the bug is in when Hibernate actually issues the INSERT relative to the try/catch.
 */
@Testcontainers
@SpringBootTest
class ConcurrentRegistrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AuthService authService;

    @Test
    void concurrentSameEmailRegistrations_exactlyOneSucceeds_restGetDuplicateEmailException() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger duplicateExceptions = new AtomicInteger();
        List<Throwable> otherExceptions = new CopyOnWriteArrayList<>();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    authService.register(new RegisterRequest("race@example.com", "secret123"));
                    successes.incrementAndGet();
                } catch (DuplicateEmailException e) {
                    duplicateExceptions.incrementAndGet();
                } catch (Throwable t) {
                    otherExceptions.add(t);
                }
            }));
        }

        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(otherExceptions).isEmpty();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(duplicateExceptions.get()).isEqualTo(threads - 1);
    }
}
