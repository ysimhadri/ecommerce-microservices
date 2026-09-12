package com.ecommerce.portal.service;

import com.ecommerce.portal.dto.ServiceRegisterRequest;
import com.ecommerce.portal.exception.DuplicateServiceNameException;
import com.ecommerce.portal.model.ServiceRole;
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
 * Regression test for the register-path concurrency race: {@code RegisteredService.id}
 * is a client-assigned UUID, so without implementing {@code Persistable} (forcing
 * {@code persist()} over {@code merge()}) AND {@code saveAndFlush(...)} (forcing the
 * INSERT to happen synchronously instead of at transaction-commit time), a concurrent
 * duplicate-name {@code save()} lets the unique-constraint violation escape
 * {@link ServiceRegistryService#register}'s try/catch uncaught. Real threads, real
 * Postgres (Testcontainers) - mirrors {@code auth-service}'s and
 * {@code product-catalog-service}'s identical tests for the same underlying JPA bug.
 */
@Testcontainers
@SpringBootTest
class ConcurrentRegistrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ServiceRegistryService serviceRegistryService;

    @Test
    void concurrentSameNameRegistrations_exactlyOneSucceeds_restGetDuplicateServiceNameException() throws Exception {
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
                    serviceRegistryService.register(
                            new ServiceRegisterRequest("race-service", "Race Service", null, ServiceRole.BOTH));
                    successes.incrementAndGet();
                } catch (DuplicateServiceNameException e) {
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
