package com.aiblockclock.domain.payment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("container")
class PaymentMigrationMySqlTest {

    private static final int MYSQL_CHECK_CONSTRAINT_VIOLATION = 3819;

    @Test
    void V2의_기존_금액을_보존하고_정밀도와_양수_제약을_검증한다() {
        try (MySQLContainer mysql = new MySQLContainer("mysql:8.4")
                .withDatabaseName("migration_test")) {
            mysql.start();
            var dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Flyway.configure().dataSource(dataSource).target("2").load().migrate();
            BigDecimal largestBalance = new BigDecimal("99999999999999999999999999999999999.999");
            jdbc.update("INSERT INTO ledger_account (account_type, reference_id, balance) VALUES ('ABC_BALANCE', 'existing-user', ?)",
                    largestBalance);
            jdbc.update("INSERT INTO charge_request (user_reference_id, sender_wallet_address, chain_id, "
                    + "transaction_hash, requested_deposit_amount, credited_abc_amount, status) "
                    + "VALUES ('existing-user', 'existing-wallet', 12345, 'existing-hash', 10.123, 10.123, 'CONFIRMED')");
            jdbc.update("INSERT INTO ledger_transaction (event_type, idempotency_key, reference_type, reference_id) "
                    + "VALUES ('CHARGE', 'migration-key', 'CHARGE_REQUEST', '1')");
            jdbc.update("INSERT INTO ledger_entry (ledger_transaction_id, ledger_account_id, amount_delta) VALUES (1, 1, 10.123)");

            Flyway flyway = Flyway.configure().dataSource(dataSource).load();
            flyway.migrate();
            flyway.validate();
            assertThat(List.of(flyway.info().applied()).stream()
                    .map(migration -> migration.getVersion().getVersion()).toList()).containsExactly("1", "2", "2.1");
            assertThat(jdbc.queryForObject("SELECT balance FROM ledger_account WHERE id = 1", BigDecimal.class))
                    .isEqualByComparingTo(largestBalance);
            assertThat(jdbc.queryForObject("SELECT credited_abc_amount FROM charge_request WHERE id = 1", BigDecimal.class))
                    .isEqualByComparingTo("10.123");
            assertThat(jdbc.queryForObject("SELECT amount_delta FROM ledger_entry WHERE id = 1", BigDecimal.class))
                    .isEqualByComparingTo("10.123");
            assertThat(jdbc.queryForList("SELECT NUMERIC_SCALE FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME IN ('balance', 'amount_delta', 'credited_abc_amount')", Integer.class))
                    .hasSize(3).containsOnly(6);
            assertThat(jdbc.queryForList("SELECT NUMERIC_PRECISION FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME IN ('balance', 'amount_delta', 'credited_abc_amount')", Integer.class))
                    .hasSize(3).containsOnly(41);

            for (String invalidAmount : List.of("0", "-0.000001")) {
                assertCheckViolation(() -> jdbc.update("UPDATE charge_request SET requested_deposit_amount = ? WHERE id = 1",
                        new BigDecimal(invalidAmount)), "ck_charge_request_deposit_amount_positive");
                assertCheckViolation(() -> jdbc.update("UPDATE charge_request SET credited_abc_amount = ? WHERE id = 1",
                        new BigDecimal(invalidAmount)), "ck_charge_request_abc_amount_positive");
            }
            jdbc.update("UPDATE charge_request SET credited_abc_amount = NULL WHERE id = 1");
            jdbc.update("UPDATE ledger_entry SET amount_delta = 1.0000005 WHERE id = 1");
            assertThat(jdbc.queryForObject("SELECT amount_delta FROM ledger_entry WHERE id = 1", BigDecimal.class))
                    .isEqualByComparingTo("1.000001");
            jdbc.update("UPDATE ledger_entry SET amount_delta = 0.000001 WHERE id = 1");
            assertThat(jdbc.queryForObject("SELECT amount_delta FROM ledger_entry WHERE id = 1", BigDecimal.class))
                    .isEqualByComparingTo("0.000001");
        }
    }

    private void assertCheckViolation(Runnable update, String constraintName) {
        assertThatThrownBy(update::run).isInstanceOfSatisfying(DataAccessException.class, exception ->
                assertThat(exception.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, sqlException -> {
                    assertThat(sqlException.getErrorCode()).isEqualTo(MYSQL_CHECK_CONSTRAINT_VIOLATION);
                    assertThat(sqlException.getMessage()).contains(constraintName);
                }));
    }
}
