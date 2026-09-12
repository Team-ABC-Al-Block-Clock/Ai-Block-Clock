package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class CreditTransactionFilterIntegrationTest {

    @Autowired
    private CreditService creditService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void 기간과_거래_종류로_ABC_사용_내역을_조회한다() {
        LedgerAccountEntity issuanceAccount =
                saveAccount(
                        LedgerAccountType.ABC_ISSUANCE,
                        "SYSTEM"
                );

        LedgerAccountEntity userAccount =
                saveAccount(
                        LedgerAccountType.ABC_BALANCE,
                        "user-a"
                );

        LedgerAccountEntity escrowAccount =
                saveAccount(
                        LedgerAccountType.EXECUTION_ESCROW,
                        "execution-a"
                );

        recordTransfer(
                LedgerEventType.CHARGE,
                "filter-charge",
                "CHARGE_REQUEST",
                "101",
                issuanceAccount,
                userAccount,
                "10.000"
        );

        LedgerTransactionEntity reserveTransaction =
                recordTransfer(
                        LedgerEventType.RESERVE,
                        "filter-reserve",
                        "EXECUTION",
                        "201",
                        userAccount,
                        escrowAccount,
                        "3.000"
                );

        LocalDate createdDate =
                reserveTransaction.getCreatedAt().toLocalDate();

        CreditTransactionSearchCondition condition =
                CreditTransactionSearchCondition.betweenDates(
                        1,
                        createdDate,
                        createdDate,
                        LedgerEventType.RESERVE
                );

        CreditTransactionPage transactionPage =
                creditService.readTransactions(
                        "user-a",
                        condition
                );

        assertThat(transactionPage.items())
                .extracting(CreditTransaction::transactionId)
                .containsExactly(reserveTransaction.getId());

        assertThat(transactionPage.items().get(0).eventType())
                .isEqualTo(LedgerEventType.RESERVE);
    }

    @Test
    void 조회_기간_밖의_ABC_사용_내역은_제외한다() {
        LedgerAccountEntity issuanceAccount =
                saveAccount(
                        LedgerAccountType.ABC_ISSUANCE,
                        "SYSTEM"
                );

        LedgerAccountEntity userAccount =
                saveAccount(
                        LedgerAccountType.ABC_BALANCE,
                        "user-a"
                );

        LedgerTransactionEntity chargeTransaction =
                recordTransfer(
                        LedgerEventType.CHARGE,
                        "outside-period-charge",
                        "CHARGE_REQUEST",
                        "301",
                        issuanceAccount,
                        userAccount,
                        "5.000"
                );

        LocalDate previousDate =
                chargeTransaction.getCreatedAt()
                        .toLocalDate()
                        .minusDays(1);

        CreditTransactionSearchCondition condition =
                CreditTransactionSearchCondition.betweenDates(
                        1,
                        previousDate,
                        previousDate,
                        null
                );

        CreditTransactionPage transactionPage =
                creditService.readTransactions(
                        "user-a",
                        condition
                );

        assertThat(transactionPage.items()).isEmpty();
        assertThat(transactionPage.totalElements()).isZero();
    }

    private LedgerAccountEntity saveAccount(
            LedgerAccountType accountType,
            String referenceId
    ) {
        return ledgerAccountRepository.save(
                LedgerAccountEntity.of(
                        accountType,
                        referenceId
                )
        );
    }

    private LedgerTransactionEntity recordTransfer(
            LedgerEventType eventType,
            String idempotencyKey,
            String referenceType,
            String referenceId,
            LedgerAccountEntity fromAccount,
            LedgerAccountEntity toAccount,
            String amount
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

        BigDecimal transferAmount = new BigDecimal(amount);

        ledgerEntryRepository.saveAllAndFlush(
                List.of(
                        LedgerEntryEntity.of(
                                transaction,
                                fromAccount,
                                transferAmount.negate()
                        ),
                        LedgerEntryEntity.of(
                                transaction,
                                toAccount,
                                transferAmount
                        )
                )
        );

        return transaction;
    }
}
