package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AbcChargeLedgerWriter {

    private static final String SYSTEM_REFERENCE_ID = "SYSTEM";
    private static final String REFERENCE_TYPE = "CHARGE_REQUEST";

    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public LedgerTransactionEntity recordCharge(
            ChargeRequestEntity chargeRequest,
            BigDecimal abcAmount
    ) {
        if (chargeRequest == null || chargeRequest.getId() == null) {
            throw new CoreException(
                    ErrorType.CHARGE_INVALID_STATE
            );
        }

        if (abcAmount == null || abcAmount.signum() <= 0) {
            throw new CoreException(
                    ErrorType.CHARGE_INVALID_AMOUNT
            );
        }

        String idempotencyKey =
                "charge-request:" + chargeRequest.getId();

        return ledgerTransactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseGet(
                        () -> createLedgerTransaction(
                                chargeRequest,
                                abcAmount,
                                idempotencyKey
                        )
                );
    }

    private LedgerTransactionEntity createLedgerTransaction(
            ChargeRequestEntity chargeRequest,
            BigDecimal abcAmount,
            String idempotencyKey
    ) {
        LedgerAccountEntity issuanceAccount =
                findOrCreateAccount(
                        LedgerAccountType.ABC_ISSUANCE,
                        SYSTEM_REFERENCE_ID
                );

        LedgerAccountEntity userAccount =
                findOrCreateAccount(
                        LedgerAccountType.ABC_BALANCE,
                        chargeRequest.getUserReferenceId()
                );

        LedgerTransactionEntity transaction =
                ledgerTransactionRepository.save(
                        LedgerTransactionEntity.of(
                                LedgerEventType.CHARGE,
                                idempotencyKey,
                                REFERENCE_TYPE,
                                chargeRequest.getId().toString(),
                                chargeRequest.getChainId(),
                                chargeRequest.getTransactionHash()
                        )
                );

        ledgerEntryRepository.saveAll(
                List.of(
                        LedgerEntryEntity.of(
                                transaction,
                                issuanceAccount,
                                abcAmount.negate()
                        ),
                        LedgerEntryEntity.of(
                                transaction,
                                userAccount,
                                abcAmount
                        )
                )
        );

        issuanceAccount.modifyBalance(
                issuanceAccount.getBalance()
                        .subtract(abcAmount)
        );

        userAccount.modifyBalance(
                userAccount.getBalance()
                        .add(abcAmount)
        );

        return transaction;
    }

    private LedgerAccountEntity findOrCreateAccount(
            LedgerAccountType accountType,
            String referenceId
    ) {
        return ledgerAccountRepository
                .findByAccountTypeAndReferenceId(
                        accountType,
                        referenceId
                )
                .orElseGet(
                        () -> ledgerAccountRepository.save(
                                LedgerAccountEntity.of(
                                        accountType,
                                        referenceId
                                )
                        )
                );
    }
}
