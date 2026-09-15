package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
class ChargeRequestStateConcurrencyIntegrationTest {

    @Autowired
    private ChargeRequestReader chargeRequestReader;

    @Autowired
    private ChargeRequestWriter chargeRequestWriter;

    @Autowired
    private ChargeConfirmationManager chargeConfirmationManager;

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAllInBatch();
        ledgerTransactionRepository.deleteAllInBatch();
        ledgerAccountRepository.deleteAllInBatch();
        chargeRequestRepository.deleteAllInBatch();
    }

    @Test
    void 확정된_충전은_이전에_읽은_실패_결과로_덮어쓸_수_없다() {
        ChargeRequestEntity pendingChargeRequest =
                chargeRequestRepository.saveAndFlush(
                        ChargeRequestEntity.pending(
                                "user-concurrency-100",
                                "0x1111111111111111111111111111111111111111",
                                12345L,
                                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                new BigDecimal("10.000000")
                        )
                );

        ChargeRequest staleChargeRequest =
                chargeRequestReader.read(
                        pendingChargeRequest.getId()
                );

        ChargeRequest confirmationCandidate =
                chargeRequestReader.read(
                        pendingChargeRequest.getId()
                );

        ChargeRequest confirmedChargeRequest =
                chargeConfirmationManager.confirm(
                        confirmationCandidate,
                        new BigDecimal("10.000")
                );

        staleChargeRequest.fail(
                ChargeStatusReason.ONCHAIN_TRANSACTION_FAILED
        );

        assertThatThrownBy(
                () -> chargeRequestWriter.updateFromPending(
                        staleChargeRequest
                )
        )
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception -> assertThat(
                                exception.getErrorType()
                        ).isEqualTo(
                                ErrorType.CHARGE_REQUEST_CONCURRENTLY_MODIFIED
                        )
                );

        ChargeRequestEntity persistedChargeRequest =
                chargeRequestRepository
                        .findById(pendingChargeRequest.getId())
                        .orElseThrow();

        assertThat(confirmedChargeRequest.getStatus())
                .isEqualTo(ChargeStatus.CONFIRMED);
        assertThat(persistedChargeRequest.getStatus())
                .isEqualTo(ChargeStatus.CONFIRMED);
        assertThat(persistedChargeRequest.getCreditedAbcAmount())
                .isEqualByComparingTo("10.000");
        assertThat(ledgerTransactionRepository.count())
                .isEqualTo(1);
    }

    @Test
    void 실패된_충전은_이전에_읽은_객체로_확정하거나_원장에_기록할_수_없다() {
        ChargeRequestEntity pendingChargeRequest =
                chargeRequestRepository.saveAndFlush(
                        ChargeRequestEntity.pending(
                                "user-concurrency-200",
                                "0x2222222222222222222222222222222222222222",
                                12345L,
                                "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                                new BigDecimal("10.000000")
                        )
                );

        ChargeRequest staleConfirmationCandidate =
                chargeRequestReader.read(
                        pendingChargeRequest.getId()
                );

        ChargeRequest failureCandidate =
                chargeRequestReader.read(
                        pendingChargeRequest.getId()
                );

        failureCandidate.fail(
                ChargeStatusReason.ONCHAIN_TRANSACTION_FAILED
        );
        chargeRequestWriter.updateFromPending(failureCandidate);

        assertThatThrownBy(
                () -> chargeConfirmationManager.confirm(
                        staleConfirmationCandidate,
                        new BigDecimal("10.000")
                )
        )
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception -> assertThat(
                                exception.getErrorType()
                        ).isEqualTo(
                                ErrorType.CHARGE_REQUEST_CONCURRENTLY_MODIFIED
                        )
                );

        ChargeRequestEntity persistedChargeRequest =
                chargeRequestRepository
                        .findById(pendingChargeRequest.getId())
                        .orElseThrow();

        assertThat(persistedChargeRequest.getStatus())
                .isEqualTo(ChargeStatus.FAILED);
        assertThat(persistedChargeRequest.getCreditedAbcAmount())
                .isNull();
        assertThat(ledgerTransactionRepository.count())
                .isZero();
    }
}
