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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class LedgerTransactionWriterIntegrationTest {

    private static final String SYSTEM_ACCOUNT_REFERENCE_ID = "SYSTEM";

    @Autowired
    private LedgerTransactionWriter ledgerTransactionWriter;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void 원장_거래와_계정별_증감_항목을_기록한다() {
        LedgerTransaction ledgerTransaction =
                createLedgerTransaction(
                        "charge-request:100",
                        "100",
                        "user-ledger-100"
                );

        ledgerTransactionWriter.append(ledgerTransaction);

        ledgerTransactionRepository.flush();
        ledgerEntryRepository.flush();
        ledgerAccountRepository.flush();

        LedgerTransactionEntity savedLedgerTransaction =
                ledgerTransactionRepository
                        .findByIdempotencyKey(
                                ledgerTransaction.idempotencyKey()
                        )
                        .orElseThrow();

        assertThat(savedLedgerTransaction.getEventType())
                .isEqualTo(LedgerEventType.CHARGE);
        assertThat(savedLedgerTransaction.getReferenceId())
                .isEqualTo("100");
        assertThat(savedLedgerTransaction.getChainId())
                .isEqualTo(12345L);

        List<LedgerEntryEntity> savedLedgerEntries =
                ledgerEntryRepository
                        .findAllByTransactionIdOrderByIdAsc(
                                savedLedgerTransaction.getId()
                        );

        assertThat(savedLedgerEntries)
                .hasSize(2);

        BigDecimal totalAmountDelta =
                savedLedgerEntries.stream()
                        .map(LedgerEntryEntity::getAmountDelta)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        assertThat(totalAmountDelta)
                .isEqualByComparingTo("0.000");

        LedgerAccountEntity issuanceAccount =
                findLedgerAccount(
                        LedgerAccountType.ABC_ISSUANCE,
                        SYSTEM_ACCOUNT_REFERENCE_ID
                );

        LedgerAccountEntity userAccount =
                findLedgerAccount(
                        LedgerAccountType.ABC_BALANCE,
                        "user-ledger-100"
                );

        assertThat(issuanceAccount.getBalance())
                .isEqualByComparingTo("-10.000");
        assertThat(userAccount.getBalance())
                .isEqualByComparingTo("10.000");
    }

    @Test
    void 동일한_멱등_키의_원장_거래는_중복_기록하지_않는다() {
        LedgerTransaction ledgerTransaction =
                createLedgerTransaction(
                        "charge-request:200",
                        "200",
                        "user-ledger-200"
                );

        ledgerTransactionWriter.append(ledgerTransaction);
        ledgerTransactionWriter.append(ledgerTransaction);

        ledgerTransactionRepository.flush();
        ledgerEntryRepository.flush();
        ledgerAccountRepository.flush();

        assertThat(ledgerTransactionRepository.count())
                .isEqualTo(1);

        LedgerAccountEntity userAccount =
                findLedgerAccount(
                        LedgerAccountType.ABC_BALANCE,
                        "user-ledger-200"
                );

        assertThat(userAccount.getBalance())
                .isEqualByComparingTo("10.000");
    }

    private LedgerTransaction createLedgerTransaction(
            String idempotencyKey,
            String referenceId,
            String userReferenceId
    ) {
        BigDecimal creditedAbcAmount =
                new BigDecimal("10.000");

        return new LedgerTransaction(
                LedgerEventType.CHARGE,
                idempotencyKey,
                "CHARGE_REQUEST",
                referenceId,
                12345L,
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                List.of(
                        new LedgerEntry(
                                LedgerAccountType.ABC_ISSUANCE,
                                SYSTEM_ACCOUNT_REFERENCE_ID,
                                creditedAbcAmount.negate()
                        ),
                        new LedgerEntry(
                                LedgerAccountType.ABC_BALANCE,
                                userReferenceId,
                                creditedAbcAmount
                        )
                )
        );
    }

    private LedgerAccountEntity findLedgerAccount(
            LedgerAccountType accountType,
            String accountReferenceId
    ) {
        return ledgerAccountRepository
                .findByAccountTypeAndReferenceId(
                        accountType,
                        accountReferenceId
                )
                .orElseThrow();
    }
}
