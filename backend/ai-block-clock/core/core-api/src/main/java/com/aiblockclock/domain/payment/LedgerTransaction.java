package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private static final String PLATFORM_ACCOUNT_REFERENCE_ID = "PLATFORM";
    private static final String CHARGE_REQUEST_REFERENCE_TYPE =
            "CHARGE_REQUEST";
    private static final String CHARGE_IDEMPOTENCY_KEY_PREFIX =
            "charge-request:";
    private static final String EXECUTION_REFERENCE_TYPE = "EXECUTION";
    private static final String EXECUTION_RESERVE_KEY_PREFIX = "execution-reserve:";
    private static final String EXECUTION_RELEASE_KEY_PREFIX = "execution-release:";
    private static final String EXECUTION_REFUND_KEY_PREFIX = "execution-refund:";
    private static final String EXECUTION_SETTLEMENT_KEY_PREFIX = "execution-settlement:";

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

    // 끝난 실행에서 쓰지 않은 만큼을 예치에서 돌려준다. 쓴 만큼은 같은 자리에서 정산(forExecutionSettlement)으로 옮긴다.
    // 키가 실행 ID 라 폴링이 같은 끝을 여러 번 봐도 한 번만 기록된다.
    public static LedgerTransaction forExecutionRefund(
            UUID executionId,
            UserId payer,
            AbcAmount refundAmount
    ) {
        String payerReferenceId = referenceIdOf(payer);

        return new LedgerTransaction(
                LedgerEventType.REFUND,
                EXECUTION_REFUND_KEY_PREFIX + executionId,
                EXECUTION_REFERENCE_TYPE,
                executionId.toString(),
                null,
                null,
                List.of(
                        new LedgerEntry(LedgerAccountType.EXECUTION_ESCROW, payerReferenceId,
                                refundAmount.value().negate()),
                        new LedgerEntry(LedgerAccountType.ABC_BALANCE, payerReferenceId,
                                refundAmount.value())
                )
        );
    }

    // 끝난 실행에서 쓴 만큼을 예치에서 빼 단계 주인들의 정산금으로 옮긴다. 수수료가 없어 뺀 만큼이 모두 간다.
    // 주인이 여럿이어도 한 거래로 적어, 일부만 옮겨지고 예치가 남는 일이 없다. 키가 실행 ID 라 한 번만 기록된다.
    // 자기 에이전트를 자기가 쓴 실행은 내는 사람과 받는 사람이 같다. 쓸 수 있는 잔액이 출금할 수 있는 정산금으로 옮겨 간다.
    public static LedgerTransaction forExecutionSettlement(
            UUID executionId,
            UserId payer,
            Map<UserId, AbcAmount> payouts
    ) {
        BigDecimal used = payouts.values().stream()
                .map(AbcAmount::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<LedgerEntry> entries = new ArrayList<>();

        entries.add(new LedgerEntry(LedgerAccountType.EXECUTION_ESCROW, referenceIdOf(payer), used.negate()));
        payouts.forEach((supplier, amount) ->
                entries.add(new LedgerEntry(LedgerAccountType.PAYOUT_BALANCE, referenceIdOf(supplier), amount.value())));

        return new LedgerTransaction(
                LedgerEventType.SETTLEMENT,
                EXECUTION_SETTLEMENT_KEY_PREFIX + executionId,
                EXECUTION_REFERENCE_TYPE,
                executionId.toString(),
                null,
                null,
                entries
        );
    }

    // 플랫폼 탓으로 실패한 실행에서 앞서 성공한 단계의 주인에게 줄 값을 플랫폼이 대신 낸다 (엔진 계약 v2.0 8.5).
    // 사용자의 예치는 환불(forExecutionRefund)로 모두 돌려주고, 여기서는 플랫폼 부담 계정에서 빼 정산금으로 옮긴다.
    // 사용자 몫의 정산과 키가 같아 한 실행에 정산은 한 번만 기록된다.
    public static LedgerTransaction forPlatformCoveredSettlement(
            UUID executionId,
            Map<UserId, AbcAmount> payouts
    ) {
        BigDecimal covered = payouts.values().stream()
                .map(AbcAmount::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<LedgerEntry> entries = new ArrayList<>();

        entries.add(new LedgerEntry(LedgerAccountType.PLATFORM_EXPENSE, PLATFORM_ACCOUNT_REFERENCE_ID,
                covered.negate()));
        payouts.forEach((supplier, amount) ->
                entries.add(new LedgerEntry(LedgerAccountType.PAYOUT_BALANCE, referenceIdOf(supplier), amount.value())));

        return new LedgerTransaction(
                LedgerEventType.SETTLEMENT,
                EXECUTION_SETTLEMENT_KEY_PREFIX + executionId,
                EXECUTION_REFERENCE_TYPE,
                executionId.toString(),
                null,
                null,
                entries
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
