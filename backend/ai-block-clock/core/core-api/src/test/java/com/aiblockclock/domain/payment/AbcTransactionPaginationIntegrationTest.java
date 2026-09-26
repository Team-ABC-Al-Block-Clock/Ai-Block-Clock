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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class AbcTransactionPaginationIntegrationTest {

    @Autowired
    private AbcLedgerQueryService abcLedgerQueryService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void ABC_사용_내역은_커서로_이어서_조회한다() {
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

        AbcTransactionSearchCondition condition = AbcTransactionSearchCondition.allDates(null);

        CursorPage<AbcTransaction> firstPage =
                abcLedgerQueryService.readTransactions(UserId.of(1L), condition, null, 10);

        assertThat(firstPage.content())
                .extracting(AbcTransaction::transactionId)
                .containsExactlyElementsOf(
                        reversedRange(transactionIds, 2, 12)
                );
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.nextCursor()).isNotNull();

        CursorPage<AbcTransaction> secondPage =
                abcLedgerQueryService.readTransactions(UserId.of(1L), condition, firstPage.nextCursor(), 10);

        assertThat(secondPage.content())
                .extracting(AbcTransaction::transactionId)
                .containsExactlyElementsOf(
                        reversedRange(transactionIds, 0, 2)
                );
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
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
