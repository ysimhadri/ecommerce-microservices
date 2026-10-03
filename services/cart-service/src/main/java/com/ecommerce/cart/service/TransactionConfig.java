package com.ecommerce.cart.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class TransactionConfig {

    @Bean
    public TransactionOperations transactionOperations(PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }
}
