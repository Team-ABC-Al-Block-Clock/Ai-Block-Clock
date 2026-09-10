package com.aiblockclock.application.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AbcChargeLedgerWriter {

    private static final String SYSTEM_REFERENCE_ID = "SYSTEM";
    private static final String REFERENCE_TYPE = "CHARGE_REQUEST";

    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AbcChargeLedgerWriter(
            LedgerAccountRepository ledgerAccountRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public LedgerTransactionEntity recordCharge(
            ChargeRequestEntity chargeRequest,
            BigDecimal abcAmount
    ) {
        if (chargeRequest.getId() == null) {
            throw new IllegalArgumentException(
                    "저장된 충전 요청만 원장에 기록할 수 있습니다."
            );
        }

        if (abcAmount == null || abcAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "원장에 기록할 ABC 수량은 0보다 커야 합니다."
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
