package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class PaymentLedgerIntegrationTest {

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void 동일한_멱등성_키의_거래는_중복_저장되지_않는다() {
        LedgerTransactionEntity firstTransaction =
                LedgerTransactionEntity.of(
                        LedgerEventType.CHARGE,
                        "charge-request-100",
                        "CHARGE",
                        "100",
                        null,
                        null
                );

        LedgerTransactionEntity duplicateTransaction =
                LedgerTransactionEntity.of(
                        LedgerEventType.CHARGE,
                        "charge-request-100",
                        "CHARGE",
                        "101",
                        null,
                        null
                );

        ledgerTransactionRepository.saveAndFlush(firstTransaction);

        assertThatThrownBy(
                () -> ledgerTransactionRepository.saveAndFlush(duplicateTransaction)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 동일한_체인과_트랜잭션_해시는_중복_저장되지_않는다() {
        LedgerTransactionEntity firstTransaction =
                LedgerTransactionEntity.of(
                        LedgerEventType.CHARGE,
                        "charge-request-200",
                        "CHARGE",
                        "200",
                        11155111L,
                        "0xabc123"
                );

        LedgerTransactionEntity duplicateTransaction =
                LedgerTransactionEntity.of(
                        LedgerEventType.CHARGE,
                        "charge-request-201",
                        "CHARGE",
                        "201",
                        11155111L,
                        "0xabc123"
                );

        ledgerTransactionRepository.saveAndFlush(firstTransaction);

        assertThatThrownBy(
                () -> ledgerTransactionRepository.saveAndFlush(duplicateTransaction)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 거래에_연결된_장부_항목으로_잔액_변화_원인을_추적할_수_있다() {
        LedgerAccountEntity issuanceAccount =
                ledgerAccountRepository.save(
                        LedgerAccountEntity.of(
                                LedgerAccountType.ABC_ISSUANCE,
                                "SYSTEM"
                        )
                );

        LedgerAccountEntity userAccount =
                ledgerAccountRepository.save(
                        LedgerAccountEntity.of(
                                LedgerAccountType.ABC_BALANCE,
                                "user-300"
                        )
                );

        LedgerTransactionEntity transaction =
                ledgerTransactionRepository.save(
                        LedgerTransactionEntity.of(
                                LedgerEventType.CHARGE,
                                "charge-request-300",
                                "CHARGE",
                                "300",
                                11155111L,
                                "0xdef456"
                        )
                );

        ledgerEntryRepository.saveAndFlush(
                LedgerEntryEntity.of(
                        transaction,
                        issuanceAccount,
                        new BigDecimal("-3.000")
                )
        );

        ledgerEntryRepository.saveAndFlush(
                LedgerEntryEntity.of(
                        transaction,
                        userAccount,
                        new BigDecimal("3.000")
                )
        );

        var entries =
                ledgerEntryRepository.findAllByTransactionIdOrderByIdAsc(
                        transaction.getId()
                );

        BigDecimal totalDelta = entries.stream()
                .map(LedgerEntryEntity::getAmountDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(entries).hasSize(2);

        assertThat(entries.get(0).getAccount().getId())
                .isEqualTo(issuanceAccount.getId());

        assertThat(entries.get(0).getAmountDelta())
                .isEqualByComparingTo("-3.000");

        assertThat(entries.get(1).getAccount().getId())
                .isEqualTo(userAccount.getId());

        assertThat(entries.get(1).getAmountDelta())
                .isEqualByComparingTo("3.000");

        assertThat(totalDelta)
                .isEqualByComparingTo("0.000");
    }

    @Test
    void ABC_잔액과_예치금과_정산금은_각각_장부_합계와_일치한다() {
        LedgerAccountEntity issuanceAccount =
                ledgerAccountRepository.save(
                        LedgerAccountEntity.of(
                                LedgerAccountType.ABC_ISSUANCE,
                                "SYSTEM"
                        )
                );

        LedgerAccountEntity userAccount =
                ledgerAccountRepository.save(
                        LedgerAccountEntity.of(
                                LedgerAccountType.ABC_BALANCE,
                                "user-400"
                        )
                );

        LedgerAccountEntity escrowAccount =
                ledgerAccountRepository.save(
                        LedgerAccountEntity.of(
                                LedgerAccountType.EXECUTION_ESCROW,
                                "execution-400"
                        )
                );

        LedgerAccountEntity payoutAccount =
                ledgerAccountRepository.save(
                        LedgerAccountEntity.of(
                                LedgerAccountType.PAYOUT_BALANCE,
                                "provider-400"
                        )
                );

        recordTransfer(
                LedgerEventType.CHARGE,
                "charge-request-400",
                "CHARGE",
                "400",
                issuanceAccount,
                userAccount,
                new BigDecimal("10.000")
        );

        recordTransfer(
                LedgerEventType.RESERVE,
                "reserve-execution-400",
                "EXECUTION",
                "400",
                userAccount,
                escrowAccount,
                new BigDecimal("4.000")
        );

        recordTransfer(
                LedgerEventType.SETTLEMENT,
                "settlement-execution-400",
                "EXECUTION",
                "400",
                escrowAccount,
                payoutAccount,
                new BigDecimal("3.000")
        );

        recordTransfer(
                LedgerEventType.REFUND,
                "refund-execution-400",
                "EXECUTION",
                "400",
                escrowAccount,
                userAccount,
                new BigDecimal("1.000")
        );

        issuanceAccount.applyBalanceDelta(new BigDecimal("-10.000"));
        userAccount.applyBalanceDelta(new BigDecimal("7.000"));
        escrowAccount.applyBalanceDelta(new BigDecimal("0.000"));
        payoutAccount.applyBalanceDelta(new BigDecimal("3.000"));

        ledgerAccountRepository.flush();

        assertAccountBalanceMatchesEntries(issuanceAccount);
        assertAccountBalanceMatchesEntries(userAccount);
        assertAccountBalanceMatchesEntries(escrowAccount);
        assertAccountBalanceMatchesEntries(payoutAccount);
    }

    private void recordTransfer(
            LedgerEventType eventType,
            String idempotencyKey,
            String referenceType,
            String referenceId,
            LedgerAccountEntity fromAccount,
            LedgerAccountEntity toAccount,
            BigDecimal amount
    ) {
        LedgerTransactionEntity transaction =
                ledgerTransactionRepository.save(
                        LedgerTransactionEntity.of(
                                eventType,
                                idempotencyKey,
                                referenceType,
                                referenceId,
                                null,
                                null
                        )
                );

        ledgerEntryRepository.saveAndFlush(
                LedgerEntryEntity.of(
                        transaction,
                        fromAccount,
                        amount.negate()
                )
        );

        ledgerEntryRepository.saveAndFlush(
                LedgerEntryEntity.of(
                        transaction,
                        toAccount,
                        amount
                )
        );
    }

    private void assertAccountBalanceMatchesEntries(
            LedgerAccountEntity account
    ) {
        BigDecimal entryTotal =
                ledgerEntryRepository.findAllByAccountIdOrderByIdAsc(
                                account.getId()
                        )
                        .stream()
                        .map(LedgerEntryEntity::getAmountDelta)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(account.getBalance())
                .isEqualByComparingTo(entryTotal);
    }
}
