package com.aiblockclock.domain.payment;

import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChargeRequestWriterTest {

    private final ChargeRequestRepository repository = mock(ChargeRequestRepository.class);
    private final ChargeRequestWriter writer = new ChargeRequestWriter(repository);

    @Test
    void V2의_거래_중복_제약_이름을_Writer가_인식한다() throws IOException {
        String migrationSql;
        try (InputStream migration = getClass().getResourceAsStream(
                "/db/migration/V2__create_charge_request_table.sql")) {
            assertThat(migration).as("V2 마이그레이션 리소스").isNotNull();
            migrationSql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
        }

        Matcher transactionConstraint = Pattern.compile(
                "CONSTRAINT\\s+(\\w+)\\s+UNIQUE\\s*\\(\\s*chain_id\\s*,\\s*transaction_hash\\s*\\)",
                Pattern.CASE_INSENSITIVE
        ).matcher(migrationSql);
        assertThat(transactionConstraint.find()).as("체인·거래 해시 UNIQUE 제약").isTrue();

        DataIntegrityViolationException failure = constraintFailure(transactionConstraint.group(1));
        when(repository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> writer.saveNewRequest(request()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.CHARGE_REQUEST_ALREADY_EXISTS));
    }

    @Test
    void 저장된_요청을_도메인_객체로_반환한다() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChargeRequest savedRequest = writer.saveNewRequest(request());

        assertThat(savedRequest.getUserReferenceId()).isEqualTo("test-user");
        assertThat(savedRequest.getTransactionHash()).isEqualTo("test-hash");
        assertThat(savedRequest.getRequestedDepositAmount().value())
                .isEqualByComparingTo("1.000001");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "uq_charge_request_chain_hash",
            "UQ_CHARGE_REQUEST_CHAIN_HASH_INDEX_1",
            "charge_request.uq_charge_request_chain_hash"
    })
    void 같은_거래의_중복_저장은_도메인_오류로_변환한다(String constraintName) {
        DataIntegrityViolationException failure = constraintFailure(constraintName);
        when(repository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> writer.saveNewRequest(request()))
                .isInstanceOfSatisfying(CoreException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(ErrorType.CHARGE_REQUEST_ALREADY_EXISTS);
                });
    }

    @Test
    void 다른_제약의_무결성_오류는_중복_요청으로_숨기지_않는다() {
        DataIntegrityViolationException failure = constraintFailure("other_constraint");
        when(repository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> writer.saveNewRequest(request())).isSameAs(failure);
    }

    @Test
    void 제약_이름이_없으면_중복_요청으로_숨기지_않는다() {
        DataIntegrityViolationException failure = constraintFailure(null);
        when(repository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> writer.saveNewRequest(request())).isSameAs(failure);
    }

    @Test
    void 제약_위반이_아닌_무결성_오류는_그대로_전달한다() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unknown");
        when(repository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> writer.saveNewRequest(request())).isSameAs(failure);
    }

    private DataIntegrityViolationException constraintFailure(String constraintName) {
        return new DataIntegrityViolationException("constraint failure",
                new ConstraintViolationException("constraint failure", new SQLException(), constraintName));
    }

    private ChargeRequest request() {
        return ChargeRequest.pending(
                "test-user",
                "test-wallet",
                12345L,
                "test-hash",
                DepositAmount.of(new BigDecimal("1.000001"))
        );
    }
}
