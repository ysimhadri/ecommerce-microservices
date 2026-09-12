package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.CategoryCreateRequest;
import com.ecommerce.catalog.exception.DuplicateCategoryNameException;
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
 * Regression test for the register-path concurrency race: {@code Category.id} is a
 * client-assigned UUID, so without {@link com.ecommerce.catalog.model.Category} implementing
 * {@code Persistable} (forcing {@code persist()} over {@code merge()}) AND
 * {@code saveAndFlush(...)} (forcing the INSERT to happen synchronously instead of at
 * transaction-commit time), a concurrent duplicate-name {@code save()} lets the unique-constraint
 * violation escape {@link CategoryCommandService#create}'s try/catch uncaught instead of becoming
 * the intended {@link DuplicateCategoryNameException}. Real threads, real Postgres
 * (Testcontainers) - a mocked repository can't reproduce this, since the bug is in when
 * Hibernate actually issues the INSERT relative to the try/catch. Mirrors auth-service's
 * identical {@code ConcurrentRegistrationTest}.
 */
@Testcontainers
@SpringBootTest
class ConcurrentCategoryCreationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CategoryCommandService categoryCommandService;

    @Test
    void concurrentSameNameCreates_exactlyOneSucceeds_restGetDuplicateCategoryNameException() throws Exception {
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
                    categoryCommandService.create(new CategoryCreateRequest("RaceCategory", "desc"));
                    successes.incrementAndGet();
                } catch (DuplicateCategoryNameException e) {
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
