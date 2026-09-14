package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LedgerTransactionWriter {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public void append(LedgerTransaction ledgerTransaction) {
        if (ledgerTransactionRepository
                .findByIdempotencyKey(
                        ledgerTransaction.idempotencyKey()
                )
                .isPresent()) {
            return;
        }

        LedgerTransactionEntity ledgerTransactionEntity =
                ledgerTransactionRepository.save(
                        LedgerTransactionEntity.of(
                                ledgerTransaction.eventType(),
                                ledgerTransaction.idempotencyKey(),
                                ledgerTransaction.referenceType(),
                                ledgerTransaction.referenceId(),
                                ledgerTransaction.chainId(),
                                ledgerTransaction.transactionHash()
                        )
                );

        List<LedgerEntryEntity> ledgerEntryEntities =
                ledgerTransaction.entries().stream()
                        .map(ledgerEntry -> createLedgerEntryEntity(
                                ledgerTransactionEntity,
                                ledgerEntry
                        ))
                        .toList();

        ledgerEntryRepository.saveAll(ledgerEntryEntities);
    }

    private LedgerEntryEntity createLedgerEntryEntity(
            LedgerTransactionEntity ledgerTransactionEntity,
            LedgerEntry ledgerEntry
    ) {
        LedgerAccountEntity ledgerAccountEntity =
                findOrCreateLedgerAccount(
                        ledgerEntry.accountType(),
                        ledgerEntry.accountReferenceId()
                );

        ledgerAccountEntity.applyBalanceDelta(
                ledgerEntry.amountDelta()
        );

        return LedgerEntryEntity.of(
                ledgerTransactionEntity,
                ledgerAccountEntity,
                ledgerEntry.amountDelta()
        );
    }

    private LedgerAccountEntity findOrCreateLedgerAccount(
            LedgerAccountType accountType,
            String accountReferenceId
    ) {
        return ledgerAccountRepository
                .findByAccountTypeAndReferenceId(
                        accountType,
                        accountReferenceId
                )
                .orElseGet(
                        () -> ledgerAccountRepository.save(
                                LedgerAccountEntity.of(
                                        accountType,
                                        accountReferenceId
                                )
                        )
                );
    }
}
