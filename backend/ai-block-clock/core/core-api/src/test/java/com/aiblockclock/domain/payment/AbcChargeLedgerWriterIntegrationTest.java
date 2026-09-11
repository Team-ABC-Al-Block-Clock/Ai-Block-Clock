package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class AbcChargeLedgerWriterIntegrationTest {

    @Autowired
    private AbcChargeLedgerWriter ledgerWriter;

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void 충전으로_지급한_ABC를_복식_원장에_기록한다() {
        ChargeRequestEntity chargeRequest =
                chargeRequestRepository.saveAndFlush(
                        ChargeRequestEntity.pending(
                                "user-ledger-100",
                                "0x1111111111111111111111111111111111111111",
                                12345L,
                                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                new BigDecimal("10.000000")
                        )
                );

        LedgerTransactionEntity transaction =
                ledgerWriter.recordCharge(
                        chargeRequest,
                        new BigDecimal("10.000")
                );

        ledgerTransactionRepository.flush();
        ledgerEntryRepository.flush();
        ledgerAccountRepository.flush();

        assertThat(transaction.getEventType())
                .isEqualTo(LedgerEventType.CHARGE);

        assertThat(transaction.getIdempotencyKey())
                .isEqualTo(
                        "charge-request:" + chargeRequest.getId()
                );

        assertThat(transaction.getChainId())
                .isEqualTo(12345L);

        assertThat(transaction.getTransactionHash())
                .isEqualTo(
                        chargeRequest.getTransactionHash()
                );

        List<LedgerEntryEntity> entries =
                ledgerEntryRepository
                        .findAllByTransactionIdOrderByIdAsc(
                                transaction.getId()
                        );

        assertThat(entries)
                .hasSize(2);

        BigDecimal totalDelta = entries.stream()
                .map(LedgerEntryEntity::getAmountDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalDelta)
                .isEqualByComparingTo("0.000");

        LedgerAccountEntity issuanceAccount =
                ledgerAccountRepository
                        .findByAccountTypeAndReferenceId(
                                LedgerAccountType.ABC_ISSUANCE,
                                "SYSTEM"
                        )
                        .orElseThrow();

        LedgerAccountEntity userAccount =
                ledgerAccountRepository
                        .findByAccountTypeAndReferenceId(
                                LedgerAccountType.ABC_BALANCE,
                                "user-ledger-100"
                        )
                        .orElseThrow();

        assertThat(issuanceAccount.getBalance())
                .isEqualByComparingTo("-10.000");

        assertThat(userAccount.getBalance())
                .isEqualByComparingTo("10.000");
    }

    @Test
    void 같은_충전_요청을_다시_처리해도_ABC를_중복_지급하지_않는다() {
        ChargeRequestEntity chargeRequest =
                chargeRequestRepository.saveAndFlush(
                        ChargeRequestEntity.pending(
                                "user-ledger-200",
                                "0x2222222222222222222222222222222222222222",
                                12345L,
                                "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                                new BigDecimal("10.000000")
                        )
                );

        LedgerTransactionEntity firstTransaction =
                ledgerWriter.recordCharge(
                        chargeRequest,
                        new BigDecimal("10.000")
                );

        LedgerTransactionEntity secondTransaction =
                ledgerWriter.recordCharge(
                        chargeRequest,
                        new BigDecimal("10.000")
                );

        ledgerTransactionRepository.flush();
        ledgerEntryRepository.flush();
        ledgerAccountRepository.flush();

        assertThat(secondTransaction.getId())
                .isEqualTo(firstTransaction.getId());

        assertThat(ledgerTransactionRepository.count())
                .isEqualTo(1);

        List<LedgerEntryEntity> entries =
                ledgerEntryRepository
                        .findAllByTransactionIdOrderByIdAsc(
                                firstTransaction.getId()
                        );

        assertThat(entries)
                .hasSize(2);

        LedgerAccountEntity userAccount =
                ledgerAccountRepository
                        .findByAccountTypeAndReferenceId(
                                LedgerAccountType.ABC_BALANCE,
                                "user-ledger-200"
                        )
                        .orElseThrow();

        assertThat(userAccount.getBalance())
                .isEqualByComparingTo("10.000");
    }

    @Test
    void 저장되지_않은_충전_요청은_원장에_기록하지_않는다() {
        ChargeRequestEntity unsavedChargeRequest =
                ChargeRequestEntity.pending(
                        "user-ledger-300",
                        "0x3333333333333333333333333333333333333333",
                        12345L,
                        "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                        new BigDecimal("10.000000")
                );

        assertThatThrownBy(
                () -> ledgerWriter.recordCharge(
                        unsavedChargeRequest,
                        new BigDecimal("10.000")
                )
        ).isInstanceOfSatisfying(
                CoreException.class,
                exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.CHARGE_INVALID_STATE)
        );
    }

    @Test
    void 원장에_0_이하의_ABC를_기록하지_않는다() {
        ChargeRequestEntity chargeRequest =
                chargeRequestRepository.saveAndFlush(
                        ChargeRequestEntity.pending(
                                "user-ledger-400",
                                "0x4444444444444444444444444444444444444444",
                                12345L,
                                "0xdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                                new BigDecimal("10.000000")
                        )
                );

        assertThatThrownBy(
                () -> ledgerWriter.recordCharge(
                        chargeRequest,
                        BigDecimal.ZERO
                )
        ).isInstanceOfSatisfying(
                CoreException.class,
                exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.CHARGE_INVALID_AMOUNT)
        );
    }
}
