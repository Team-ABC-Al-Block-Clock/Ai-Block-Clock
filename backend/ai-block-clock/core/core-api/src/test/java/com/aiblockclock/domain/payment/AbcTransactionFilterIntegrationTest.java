package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.support.paging.CursorPage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class AbcTransactionFilterIntegrationTest {

    @Autowired
    private AbcLedgerQueryService abcLedgerQueryService;

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
                        "1"
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
                reserveTransaction.getCreatedAt().atOffset(ZoneOffset.UTC).toLocalDate();

        AbcTransactionSearchCondition condition =
                AbcTransactionSearchCondition.betweenDates(
                        createdDate,
                        createdDate,
                        LedgerEventType.RESERVE
                );

        CursorPage<AbcTransaction> transactionPage =
                abcLedgerQueryService.readTransactions(
                        UserId.of(1L),
                        condition,
                        null,
                        10
                );

        assertThat(transactionPage.content())
                .extracting(AbcTransaction::transactionId)
                .containsExactly(reserveTransaction.getId());

        assertThat(transactionPage.content().get(0).eventType())
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
                        "1"
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
                        .atOffset(ZoneOffset.UTC)
                        .toLocalDate()
                        .minusDays(1);

        AbcTransactionSearchCondition condition =
                AbcTransactionSearchCondition.betweenDates(
                        previousDate,
                        previousDate,
                        null
                );

        CursorPage<AbcTransaction> transactionPage =
                abcLedgerQueryService.readTransactions(
                        UserId.of(1L),
                        condition,
                        null,
                        10
                );

        assertThat(transactionPage.content()).isEmpty();
        assertThat(transactionPage.hasNext()).isFalse();
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
