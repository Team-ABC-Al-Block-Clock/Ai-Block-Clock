package com.aiblockclock.domain.node;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

@TestConfiguration(proxyBeanMethods = false)
class NodeMySqlTestConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer nodeMySql() {
        return new MySQLContainer("mysql:8.4")
                .withDatabaseName("node_test")
                .withCommand("--transaction-isolation=REPEATABLE-READ");
    }
}
