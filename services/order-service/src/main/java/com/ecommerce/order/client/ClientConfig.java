package com.ecommerce.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** One {@link RestClient} per downstream service. The builder is cloned so base URLs do not leak across beans. */
@Configuration
public class ClientConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Bean
    public RestClient cartRestClient(RestClient.Builder builder,
                                      @Value("${app.clients.cart-base-url}") String baseUrl) {
        return client(builder, baseUrl);
    }

    @Bean
    public RestClient inventoryRestClient(RestClient.Builder builder,
                                           @Value("${app.clients.inventory-base-url}") String baseUrl) {
        return client(builder, baseUrl);
    }

    @Bean
    public RestClient catalogRestClient(RestClient.Builder builder,
                                         @Value("${app.clients.catalog-base-url}") String baseUrl) {
        return client(builder, baseUrl);
    }

    private static RestClient client(RestClient.Builder builder, String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        return builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
