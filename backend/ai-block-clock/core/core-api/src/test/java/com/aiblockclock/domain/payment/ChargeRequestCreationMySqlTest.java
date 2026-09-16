package com.aiblockclock.domain.payment;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@PaymentMySqlTest
class ChargeRequestCreationMySqlTest extends ChargeRequestCreationConcurrencyIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void 실제_MySQL의_RR에서_동시_생성을_검사한다() {
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        assertThat(jdbc.queryForObject("SELECT @@session.transaction_isolation", String.class))
                .isEqualTo("REPEATABLE-READ");
    }
}
