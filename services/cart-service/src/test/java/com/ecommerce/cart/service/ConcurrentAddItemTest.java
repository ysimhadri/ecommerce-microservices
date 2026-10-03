package com.ecommerce.cart.service;

import com.ecommerce.cart.client.CatalogClient;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.CatalogProduct;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** N threads add the same new product to one cart: all succeed, one item row, summed quantity. */
@Testcontainers
@SpringBootTest
class ConcurrentAddItemTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    CartService cartService;
    @MockBean
    CatalogClient catalogClient;

    @Test
    void concurrentAddsOfSameProduct_singleRowWithSummedQuantity() throws Exception {
        int threads = 8;
        UUID owner = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(catalogClient.getProduct(productId)).thenReturn(new CatalogProduct(productId, "Widget", new BigDecimal("1.00")));
        cartService.getOrCreate(owner);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<CartResponse>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return cartService.addItem(owner, productId, 2);
            }));
        }
        ready.await();
        go.countDown();
        for (Future<CartResponse> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        CartResponse result = cartService.getOrCreate(owner);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).quantity()).isEqualTo(threads * 2);
    }
}
