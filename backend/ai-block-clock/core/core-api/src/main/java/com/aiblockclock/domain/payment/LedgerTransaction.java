package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.math.BigDecimal;
import java.util.List;

public record LedgerTransaction(
        LedgerEventType eventType,
        String idempotencyKey,
        String referenceType,
        String referenceId,
        Long chainId,
        String transactionHash,
        List<LedgerEntry> entries
) {

    private static final String SYSTEM_ACCOUNT_REFERENCE_ID = "SYSTEM";
    private static final String CHARGE_REQUEST_REFERENCE_TYPE =
            "CHARGE_REQUEST";
    private static final String CHARGE_IDEMPOTENCY_KEY_PREFIX =
            "charge-request:";

    public LedgerTransaction {
        if (eventType == null
                || idempotencyKey == null
                || idempotencyKey.isBlank()
                || referenceType == null
                || referenceType.isBlank()
                || referenceId == null
                || referenceId.isBlank()
                || entries == null
                || entries.isEmpty()) {
            throw new CoreException(
                    ErrorType.LEDGER_INVALID_TRANSACTION
            );
        }

        entries = List.copyOf(entries);

        if (!isBalanced(entries)) {
            throw new CoreException(
                    ErrorType.LEDGER_INVALID_TRANSACTION
            );
        }
    }

    public static LedgerTransaction forCharge(
            ChargeRequest chargeRequest,
            AbcAmount creditedAbcAmount
    ) {
        if (chargeRequest == null
                || chargeRequest.getId() == null
                || creditedAbcAmount == null) {
            throw new CoreException(
                    ErrorType.LEDGER_INVALID_TRANSACTION
            );
        }

        BigDecimal creditedAmountValue = creditedAbcAmount.value();

        return new LedgerTransaction(
                LedgerEventType.CHARGE,
                CHARGE_IDEMPOTENCY_KEY_PREFIX
                        + chargeRequest.getId(),
                CHARGE_REQUEST_REFERENCE_TYPE,
                chargeRequest.getId().toString(),
                chargeRequest.getChainId(),
                chargeRequest.getTransactionHash(),
                List.of(
                        new LedgerEntry(
                                LedgerAccountType.ABC_ISSUANCE,
                                SYSTEM_ACCOUNT_REFERENCE_ID,
                                creditedAmountValue.negate()
                        ),
                        new LedgerEntry(
                                LedgerAccountType.ABC_BALANCE,
                                chargeRequest.getUserReferenceId(),
                                creditedAmountValue
                        )
                )
        );
    }

    public boolean isBalanced() {
        return isBalanced(entries);
    }

    private static boolean isBalanced(
            List<LedgerEntry> entries
    ) {
        BigDecimal totalAmountDelta = entries.stream()
                .map(LedgerEntry::amountDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalAmountDelta.compareTo(BigDecimal.ZERO) == 0;
    }
}
