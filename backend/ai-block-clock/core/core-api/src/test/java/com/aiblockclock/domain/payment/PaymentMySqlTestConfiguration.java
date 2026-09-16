package com.aiblockclock.domain.payment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

@TestConfiguration(proxyBeanMethods = false)
class PaymentMySqlTestConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer paymentMySql() {
        return new MySQLContainer("mysql:8.4")
                .withDatabaseName("payment_test")
                .withCommand("--transaction-isolation=REPEATABLE-READ");
    }
}
