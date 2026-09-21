package com.aiblockclock.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

// 도메인마다 설정을 두면 테스트 컨텍스트가 갈려 MySQL 컨테이너가 그 수만큼 뜬다.
// 마이그레이션은 어차피 한 벌이므로 컨테이너도 하나를 함께 쓴다.
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mySql() {
        return new MySQLContainer("mysql:8.4")
                .withDatabaseName("aiblockclock_test")
                .withCommand("--transaction-isolation=REPEATABLE-READ");
    }
}
