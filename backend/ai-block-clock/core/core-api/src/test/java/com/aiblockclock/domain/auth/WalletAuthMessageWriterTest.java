package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.WalletAuthMessageEntity;
import com.aiblockclock.storage.auth.WalletAuthMessageRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletAuthMessageWriterTest {
    private final WalletAuthMessageRepository repository = mock(WalletAuthMessageRepository.class);
    private final WalletAuthMessageWriter writer = new WalletAuthMessageWriter(repository);

    @Test
    void V5의_지갑_중복_제약만_동시_발급_충돌로_처리한다() throws IOException {
        String migrationSql;
        try (InputStream migration = getClass().getResourceAsStream(
                "/db/migration/V5__create_wallet_auth_message_table.sql")) {
            assertThat(migration).isNotNull();
            migrationSql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }

        Matcher constraint = Pattern.compile(
                "CONSTRAINT\\s+(\\w+)\\s+UNIQUE\\s*\\(\\s*wallet_address\\s*\\)",
                Pattern.CASE_INSENSITIVE
        ).matcher(migrationSql);
        assertThat(constraint.find()).isTrue();

        String databaseConstraintName = "PUBLIC." + constraint.group(1).toUpperCase(java.util.Locale.ROOT)
                + "_INDEX_1";
        when(repository.saveAndFlush(any())).thenThrow(constraintFailure(databaseConstraintName));

        assertThatThrownBy(() -> writer.append(message()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType())
                                .isEqualTo(ErrorType.WALLET_AUTH_MESSAGE_CONCURRENTLY_MODIFIED));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"another_unique_constraint", "nonce_not_null"})
    void 다른_제약_오류는_발급_충돌로_바꾸지_않는다(String constraintName) {
        DataIntegrityViolationException failure = constraintFailure(constraintName);
        when(repository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> writer.append(message())).isSameAs(failure);
    }

    @Test
    void 제약_정보가_없는_저장_오류도_그대로_전달한다() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("storage failure");
        when(repository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> writer.append(message())).isSameAs(failure);
    }

    @Test
    void 버전_충돌은_재발급을_안내하는_도메인_오류로_변환한다() {
        when(repository.saveAndFlush(any())).thenThrow(
                new ObjectOptimisticLockingFailureException(WalletAuthMessageEntity.class, 1L)
        );

        assertThatThrownBy(() -> writer.append(message()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType())
                                .isEqualTo(ErrorType.WALLET_AUTH_MESSAGE_CONCURRENTLY_MODIFIED));
    }

    private DataIntegrityViolationException constraintFailure(String constraintName) {
        return new DataIntegrityViolationException("constraint failure",
                new ConstraintViolationException("constraint failure", new SQLException(), constraintName));
    }

    private WalletAuthMessage message() {
        Instant issuedAt = Instant.parse("2026-09-17T00:00:00Z");

        return new WalletAuthMessage("0xcccccccccccccccccccccccccccccccccccccccc", "a".repeat(64),
                "issued message", issuedAt, issuedAt.plusSeconds(300), null);
    }
}
