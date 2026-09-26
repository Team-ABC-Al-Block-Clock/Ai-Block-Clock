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
    private static final String CHARGE_REQUEST_REFERENCE_TYPE =
            "CHARGE_REQUEST";
    private static final String CHARGE_IDEMPOTENCY_KEY_PREFIX =
            "charge-request:";
    private static final String EXECUTION_REFERENCE_TYPE = "EXECUTION";
    private static final String EXECUTION_RESERVE_KEY_PREFIX = "execution-reserve:";
    private static final String EXECUTION_RELEASE_KEY_PREFIX = "execution-release:";
    private static final String EXECUTION_REFUND_KEY_PREFIX = "execution-refund:";
    private static final String EXECUTION_SETTLEMENT_KEY_PREFIX = "execution-settlement:";
    private static final String WITHDRAWAL_REFERENCE_TYPE = "WITHDRAWAL";
    private static final String WITHDRAWAL_HOLD_KEY_PREFIX = "withdrawal-hold:";
    private static final String WITHDRAWAL_RELEASE_KEY_PREFIX = "withdrawal-release:";
    private static final String PAYOUT_CONVERSION_REFERENCE_TYPE = "PAYOUT_CONVERSION";
    private static final String PAYOUT_CONVERSION_KEY_PREFIX = "payout-conversion:";
    private static final String WITHDRAWAL_PAYOUT_KEY_PREFIX = "withdrawal-payout:";

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

        if (!hasAllowedShape(eventType, entries)) {
            throw new CoreException(ErrorType.LEDGER_INVALID_TRANSACTION);
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

    // 출금을 신청하면 그 금액을 정산금에서 출금 예치로 옮겨 묶는다. 키가 출금 ID 라 한 신청에 한 번만 기록된다.
    public static LedgerTransaction forWithdrawalHold(
            Long withdrawalId,
            UserId owner,
            AbcAmount amount
    ) {
        String ownerReferenceId = referenceIdOf(owner);

        return new LedgerTransaction(
                LedgerEventType.WITHDRAWAL_HOLD,
                WITHDRAWAL_HOLD_KEY_PREFIX + withdrawalId,
                WITHDRAWAL_REFERENCE_TYPE,
                withdrawalId.toString(),
                null,
                null,
                List.of(
                        new LedgerEntry(LedgerAccountType.PAYOUT_BALANCE, ownerReferenceId,
                                amount.value().negate()),
                        new LedgerEntry(LedgerAccountType.WITHDRAWAL_ESCROW, ownerReferenceId,
                                amount.value())
                )
        );
    }

    // 지급하지 않고 닫힌 출금(사용자 취소 · 관리자 반려 · 체인이 되돌린 지급)은 묶은 금액을 정산금으로 돌려놓는다.
    // 셋이 같은 키를 써서, 겹쳐도 한 신청에 한 번만 풀린다.
    public static LedgerTransaction forWithdrawalRelease(
            Long withdrawalId,
            UserId owner,
            AbcAmount amount
    ) {
        String ownerReferenceId = referenceIdOf(owner);

        return new LedgerTransaction(
                LedgerEventType.WITHDRAWAL_RELEASE,
                WITHDRAWAL_RELEASE_KEY_PREFIX + withdrawalId,
                WITHDRAWAL_REFERENCE_TYPE,
                withdrawalId.toString(),
                null,
                null,
                List.of(
                        new LedgerEntry(LedgerAccountType.WITHDRAWAL_ESCROW, ownerReferenceId,
                                amount.value().negate()),
                        new LedgerEntry(LedgerAccountType.PAYOUT_BALANCE, ownerReferenceId,
                                amount.value())
                )
        );
    }

    // 체인에서 지급이 성공한 출금의 묶은 금액을 발행 계정으로 돌려 장부 밖으로 내보낸다. 충전이 발행 계정에서 꺼내 온 것의 반대다.
    // 키가 출금 ID 라 한 신청에 한 번만 적히고, 체인 거래 해시도 함께 적어 한 거래가 두 번 쓰이지 못한다.
    public static LedgerTransaction forWithdrawalPayout(Withdrawal withdrawal, long chainId) {
        return new LedgerTransaction(
                LedgerEventType.WITHDRAWAL_PAYOUT,
                WITHDRAWAL_PAYOUT_KEY_PREFIX + withdrawal.getId(),
                WITHDRAWAL_REFERENCE_TYPE,
                withdrawal.getId().toString(),
                chainId,
                withdrawal.getPayoutTransaction().transactionHash(),
                List.of(
                        new LedgerEntry(LedgerAccountType.WITHDRAWAL_ESCROW, referenceIdOf(withdrawal.getRequester()),
                                withdrawal.getAmount().value().negate()),
                        new LedgerEntry(LedgerAccountType.ABC_ISSUANCE, SYSTEM_ACCOUNT_REFERENCE_ID,
                                withdrawal.getAmount().value())
                )
        );
    }

    // 정산금을 같은 사람의 쓸 수 있는 잔액으로 옮긴다. 출금했다가 다시 충전하지 않고 번 ABC 를 실행에 쓰게 한다.
    public static LedgerTransaction forPayoutConversion(
            UserId owner,
            PayoutConversionRequestId requestId,
            AbcAmount amount
    ) {
        String ownerReferenceId = referenceIdOf(owner);

        return new LedgerTransaction(
                LedgerEventType.PAYOUT_CONVERSION,
                payoutConversionKeyOf(owner, requestId),
                PAYOUT_CONVERSION_REFERENCE_TYPE,
                requestId.value().toString(),
                null,
                null,
                List.of(
                        new LedgerEntry(LedgerAccountType.PAYOUT_BALANCE, ownerReferenceId,
                                amount.value().negate()),
                        new LedgerEntry(LedgerAccountType.ABC_BALANCE, ownerReferenceId,
                                amount.value())
                )
        );
    }

    // 요청 번호는 화면이 만들어 남의 번호와 겹칠 수 있다. 사용자를 함께 넣어야 남의 옮기기에 막히지 않는다.
    static String payoutConversionKeyOf(UserId owner, PayoutConversionRequestId requestId) {
        return PAYOUT_CONVERSION_KEY_PREFIX + referenceIdOf(owner) + ":" + requestId.value();
    }

    public boolean isBalanced() {
        return isBalanced(entries);
    }

    // 장부 계정은 가입 때 LedgerAccountWriter 가 같은 규칙(사용자 ID 의 십진 문자열)으로 만든다.
    // 장부 계정과 충전 요청이 사용자를 가리키는 문자열이다. 두 곳이 같은 규칙을 써야 서로 이어진다
    static String referenceIdOf(UserId userId) {
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

    // ABC 는 사용자끼리 넘길 수 없다(PAY-004). 합계가 0 이어도 A 의 잔액에서 B 의 잔액으로 가는 거래가 있으므로
    // 거래 종류마다 빠지는 칸과 들어가는 칸을 정해 두고 그 모양만 받는다. 남에게 가는 길은 정산금 적립 하나뿐이다.
    // 생성자에서 보므로 팩터리를 거치지 않은 거래도 걸리고, 종류가 늘면 switch 가 컴파일을 막는다.
    private static boolean hasAllowedShape(
            LedgerEventType eventType,
            List<LedgerEntry> entries
    ) {
        List<LedgerEntry> outflows = entries.stream()
                .filter(entry -> entry.amountDelta().signum() < 0)
                .toList();
        List<LedgerEntry> inflows = entries.stream()
                .filter(entry -> entry.amountDelta().signum() > 0)
                .toList();

        if (outflows.size() != 1) {
            return false;
        }

        LedgerEntry outflow = outflows.get(0);

        return switch (eventType) {
            case CHARGE -> outflow.accountType() == LedgerAccountType.ABC_ISSUANCE
                    && outflow.accountReferenceId().equals(SYSTEM_ACCOUNT_REFERENCE_ID)
                    && inflows.size() == 1
                    && inflows.get(0).accountType() == LedgerAccountType.ABC_BALANCE;
            case RESERVE -> isOwnMove(outflow, inflows,
                    LedgerAccountType.ABC_BALANCE, LedgerAccountType.EXECUTION_ESCROW);
            case REFUND -> isOwnMove(outflow, inflows,
                    LedgerAccountType.EXECUTION_ESCROW, LedgerAccountType.ABC_BALANCE);
            case SETTLEMENT -> outflow.accountType() == LedgerAccountType.EXECUTION_ESCROW
                    && inflows.stream()
                            .allMatch(inflow -> inflow.accountType() == LedgerAccountType.PAYOUT_BALANCE);
            case WITHDRAWAL_HOLD -> isOwnMove(outflow, inflows,
                    LedgerAccountType.PAYOUT_BALANCE, LedgerAccountType.WITHDRAWAL_ESCROW);
            case WITHDRAWAL_RELEASE -> isOwnMove(outflow, inflows,
                    LedgerAccountType.WITHDRAWAL_ESCROW, LedgerAccountType.PAYOUT_BALANCE);
            case PAYOUT_CONVERSION -> isOwnMove(outflow, inflows,
                    LedgerAccountType.PAYOUT_BALANCE, LedgerAccountType.ABC_BALANCE);
            case WITHDRAWAL_PAYOUT -> outflow.accountType() == LedgerAccountType.WITHDRAWAL_ESCROW
                    && inflows.size() == 1
                    && inflows.get(0).accountType() == LedgerAccountType.ABC_ISSUANCE
                    && inflows.get(0).accountReferenceId().equals(SYSTEM_ACCOUNT_REFERENCE_ID);
        };
    }

    // 한 사람의 칸 사이에서만 옮긴다. 받는 칸이 하나이고 주인이 빠지는 칸과 같아야 한다.
    private static boolean isOwnMove(
            LedgerEntry outflow,
            List<LedgerEntry> inflows,
            LedgerAccountType from,
            LedgerAccountType to
    ) {
        if (inflows.size() != 1) {
            return false;
        }

        LedgerEntry inflow = inflows.get(0);

        return outflow.accountType() == from
                && inflow.accountType() == to
                && inflow.accountReferenceId().equals(outflow.accountReferenceId());
    }
}
