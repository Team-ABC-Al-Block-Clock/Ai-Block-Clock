package com.aiblockclock.domain.payment;

import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;
import java.time.Instant;

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
                        .sorted(Comparator.comparing((LedgerEntry entry) -> entry.accountType().name())
                                .thenComparing(LedgerEntry::accountReferenceId))
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
                updateLedgerAccountBalance(ledgerEntry);

        return LedgerEntryEntity.of(
                ledgerTransactionEntity,
                ledgerAccountEntity,
                ledgerEntry.amountDelta()
        );
    }

    private LedgerAccountEntity updateLedgerAccountBalance(LedgerEntry ledgerEntry) {
        ledgerAccountRepository.insertIfAbsent(
                ledgerEntry.accountType().name(), ledgerEntry.accountReferenceId(), Instant.now()
        );
        Long accountId = ledgerAccountRepository.findIdForBalanceUpdate(
                ledgerEntry.accountType().name(), ledgerEntry.accountReferenceId()
        ).orElseThrow(() -> new CoreException(ErrorType.LEDGER_INVALID_TRANSACTION));

        int updatedRowCount = ledgerAccountRepository.addBalanceDelta(
                accountId, ledgerEntry.amountDelta(), Instant.now()
        );
        if (updatedRowCount != 1) {
            throw new CoreException(ErrorType.LEDGER_INVALID_TRANSACTION);
        }

        return ledgerAccountRepository.getReferenceById(accountId);
    }
}
