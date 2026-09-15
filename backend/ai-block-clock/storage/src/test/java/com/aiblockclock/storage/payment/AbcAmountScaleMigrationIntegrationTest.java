package com.aiblockclock.storage.payment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class AbcAmountScaleMigrationIntegrationTest {

    @Test
    void 기존_V2_DB의_금액과_정수부_용량을_유지하며_소수_6자리로_확장한다() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:abc_scale_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", ""
        );
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .target("2").load().migrate();
            BigDecimal existingAmount = new BigDecimal("10.123");
            BigDecimal largestExistingBalance = new BigDecimal("99999999999999999999999999999999999.999");
            jdbc.update("INSERT INTO ledger_account (id, account_type, reference_id, balance) VALUES (1, 'ABC_BALANCE', 'existing-user', ?)",
                    largestExistingBalance);
            jdbc.update("INSERT INTO ledger_transaction (id, event_type, idempotency_key, reference_type, reference_id) VALUES (1, 'CHARGE', 'migration-charge', 'CHARGE_REQUEST', '1')");
            jdbc.update("INSERT INTO ledger_entry (ledger_transaction_id, ledger_account_id, amount_delta) VALUES (1, 1, ?)",
                    existingAmount);
            jdbc.update("INSERT INTO charge_request (id, user_reference_id, sender_wallet_address, chain_id, transaction_hash, requested_usdc_amount, credited_abc_amount, status) VALUES (1, 'existing-user', '0x1111111111111111111111111111111111111111', 12345, 'existing-hash', ?, ?, 'CONFIRMED')",
                    existingAmount, existingAmount);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertStoredAmount(jdbc, "SELECT balance FROM ledger_account WHERE id = 1", largestExistingBalance);
            assertStoredAmount(jdbc, "SELECT amount_delta FROM ledger_entry", existingAmount);
            assertStoredAmount(jdbc, "SELECT credited_abc_amount FROM charge_request WHERE id = 1", existingAmount);
            for (String column : new String[]{"BALANCE", "AMOUNT_DELTA", "CREDITED_ABC_AMOUNT"}) {
                assertThat(jdbc.queryForObject("SELECT NUMERIC_PRECISION FROM INFORMATION_SCHEMA.COLUMNS WHERE COLUMN_NAME = ?",
                        Integer.class, column)).isEqualTo(41);
                assertThat(jdbc.queryForObject("SELECT NUMERIC_SCALE FROM INFORMATION_SCHEMA.COLUMNS WHERE COLUMN_NAME = ?",
                        Integer.class, column)).isEqualTo(6);
            }

            BigDecimal smallestAmount = new BigDecimal("0.000001");
            jdbc.update("UPDATE ledger_account SET balance = ? WHERE id = 1", smallestAmount);
            jdbc.update("UPDATE ledger_entry SET amount_delta = ?", smallestAmount);
            jdbc.update("UPDATE charge_request SET credited_abc_amount = ? WHERE id = 1", smallestAmount);
            assertStoredAmount(jdbc, "SELECT balance FROM ledger_account WHERE id = 1", smallestAmount);
            assertStoredAmount(jdbc, "SELECT amount_delta FROM ledger_entry", smallestAmount);
            assertStoredAmount(jdbc, "SELECT credited_abc_amount FROM charge_request WHERE id = 1", smallestAmount);
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }

    private void assertStoredAmount(JdbcTemplate jdbc, String sql, BigDecimal expected) {
        BigDecimal stored = jdbc.queryForObject(sql, BigDecimal.class);
        assertThat(stored).isEqualByComparingTo(expected);
        assertThat(stored.scale()).isEqualTo(6);
    }
}
