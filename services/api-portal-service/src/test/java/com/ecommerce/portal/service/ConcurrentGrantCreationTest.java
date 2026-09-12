package com.ecommerce.portal.service;

import com.ecommerce.portal.dto.GrantCreateRequest;
import com.ecommerce.portal.dto.ServiceCreatedResponse;
import com.ecommerce.portal.dto.ServiceRegisterRequest;
import com.ecommerce.portal.exception.DuplicateGrantException;
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
 * Regression test for the grant-creation concurrency race: unlike
 * {@code ServiceRegistryService.register}, {@code GrantService.createGrant} had no
 * try/catch around its {@code save()} at all before this fix - the V3 migration's own
 * comment states a duplicate (consumer, producer) pair "is rejected (409)... rather
 * than silently overwriting," but nothing enforced that under concurrency. Real
 * threads, real Postgres (Testcontainers) - mirrors the repo's other
 * ConcurrentRegistrationTest/ConcurrentCategoryCreationTest for the identical
 * underlying JPA bug (client-assigned UUID id needs Persistable + saveAndFlush).
 */
@Testcontainers
@SpringBootTest
class ConcurrentGrantCreationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ServiceRegistryService serviceRegistryService;

    @Autowired
    private GrantService grantService;

    @Test
    void concurrentSamePairGrants_exactlyOneSucceeds_restGetDuplicateGrantException() throws Exception {
        ServiceCreatedResponse consumer = serviceRegistryService.register(
                new ServiceRegisterRequest("race-consumer", "Race Consumer", null, ServiceRole.CONSUMER));
        ServiceCreatedResponse producer = serviceRegistryService.register(
                new ServiceRegisterRequest("race-producer", "Race Producer", null, ServiceRole.PRODUCER));

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
                    grantService.createGrant(new GrantCreateRequest(
                            consumer.id(), producer.id(), List.of("catalog:write")));
                    successes.incrementAndGet();
                } catch (DuplicateGrantException e) {
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
