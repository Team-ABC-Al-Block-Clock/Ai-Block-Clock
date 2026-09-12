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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class CreditTransactionPaginationIntegrationTest {

    @Autowired
    private CreditService creditService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void ABC_사용_내역은_1페이지부터_10개씩_조회한다() {
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

        List<Long> transactionIds = new ArrayList<>();

        for (int sequence = 1; sequence <= 12; sequence++) {
            LedgerTransactionEntity transaction =
                    recordCharge(
                            sequence,
                            issuanceAccount,
                            userAccount
                    );

            transactionIds.add(transaction.getId());
        }

        CreditTransactionPage firstPage =
                creditService.readTransactions("user-a", 1);

        CreditTransactionPage secondPage =
                creditService.readTransactions("user-a", 2);

        assertThat(firstPage.items())
                .extracting(CreditTransaction::transactionId)
                .containsExactlyElementsOf(
                        reversedRange(transactionIds, 2, 12)
                );

        assertThat(firstPage.page()).isEqualTo(1);
        assertThat(firstPage.size()).isEqualTo(10);
        assertThat(firstPage.totalElements()).isEqualTo(12);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.hasPrevious()).isFalse();
        assertThat(firstPage.hasNext()).isTrue();

        assertThat(secondPage.items())
                .extracting(CreditTransaction::transactionId)
                .containsExactlyElementsOf(
                        reversedRange(transactionIds, 0, 2)
                );

        assertThat(secondPage.page()).isEqualTo(2);
        assertThat(secondPage.size()).isEqualTo(10);
        assertThat(secondPage.totalElements()).isEqualTo(12);
        assertThat(secondPage.totalPages()).isEqualTo(2);
        assertThat(secondPage.hasPrevious()).isTrue();
        assertThat(secondPage.hasNext()).isFalse();
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

    private LedgerTransactionEntity recordCharge(
            int sequence,
            LedgerAccountEntity issuanceAccount,
            LedgerAccountEntity userAccount
    ) {
        LedgerTransactionEntity transaction =
                ledgerTransactionRepository.save(
                        LedgerTransactionEntity.of(
                                LedgerEventType.CHARGE,
                                "charge-page-" + sequence,
                                "CHARGE_REQUEST",
                                Integer.toString(sequence),
                                null,
                                null
                        )
                );

        ledgerEntryRepository.saveAllAndFlush(
                List.of(
                        LedgerEntryEntity.of(
                                transaction,
                                issuanceAccount,
                                new BigDecimal("-1.000")
                        ),
                        LedgerEntryEntity.of(
                                transaction,
                                userAccount,
                                new BigDecimal("1.000")
                        )
                )
        );

        return transaction;
    }

    private List<Long> reversedRange(
            List<Long> transactionIds,
            int fromIndex,
            int toIndex
    ) {
        List<Long> selectedIds =
                new ArrayList<>(
                        transactionIds.subList(
                                fromIndex,
                                toIndex
                        )
                );

        selectedIds.sort((first, second) ->
                Long.compare(second, first));

        return selectedIds;
    }
}
