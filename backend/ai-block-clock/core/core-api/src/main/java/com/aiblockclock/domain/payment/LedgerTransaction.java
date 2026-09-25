package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
    private static final String EXECUTION_REFERENCE_TYPE = "EXECUTION";
    private static final String EXECUTION_RESERVE_KEY_PREFIX = "execution-reserve:";
    private static final String EXECUTION_RELEASE_KEY_PREFIX = "execution-release:";

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

    // 실행을 접수할 때 쓸 수 있는 잔액에서 예치로 옮긴다. 키가 실행 ID 라 한 실행에 한 번만 기록된다.
    public static LedgerTransaction forExecutionReserve(
            UUID executionId,
            UserId payer,
            AbcAmount reservedAmount
    ) {
        String payerReferenceId = referenceIdOf(payer);

        return new LedgerTransaction(
                LedgerEventType.RESERVE,
                EXECUTION_RESERVE_KEY_PREFIX + executionId,
                EXECUTION_REFERENCE_TYPE,
                executionId.toString(),
                null,
                null,
                List.of(
                        new LedgerEntry(LedgerAccountType.ABC_BALANCE, payerReferenceId,
                                reservedAmount.value().negate()),
                        new LedgerEntry(LedgerAccountType.EXECUTION_ESCROW, payerReferenceId,
                                reservedAmount.value())
                )
        );
    }

    // 엔진이 받지 않은 실행은 예치를 그대로 되돌린다. 실행 뒤 결과를 보고 하는 환불과 키를 나눠 서로 막지 않는다.
    public static LedgerTransaction forExecutionRelease(
            UUID executionId,
            UserId payer,
            AbcAmount reservedAmount
    ) {
        String payerReferenceId = referenceIdOf(payer);

        return new LedgerTransaction(
                LedgerEventType.REFUND,
                EXECUTION_RELEASE_KEY_PREFIX + executionId,
                EXECUTION_REFERENCE_TYPE,
                executionId.toString(),
                null,
                null,
                List.of(
                        new LedgerEntry(LedgerAccountType.EXECUTION_ESCROW, payerReferenceId,
                                reservedAmount.value().negate()),
                        new LedgerEntry(LedgerAccountType.ABC_BALANCE, payerReferenceId,
                                reservedAmount.value())
                )
        );
    }

    public boolean isBalanced() {
        return isBalanced(entries);
    }

    // 장부 계정은 가입 때 LedgerAccountWriter 가 같은 규칙(사용자 ID 의 십진 문자열)으로 만든다.
    private static String referenceIdOf(UserId userId) {
        return Long.toString(userId.value());
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
