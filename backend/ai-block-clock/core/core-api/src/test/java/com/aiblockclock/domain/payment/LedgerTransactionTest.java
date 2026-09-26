package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class LedgerTransactionTest {

    @Test
    void 충전_요청으로_균형이_맞는_원장_거래를_생성한다() {
        ChargeRequest chargeRequest = persistedPendingChargeRequest();

        LedgerTransaction ledgerTransaction =
                LedgerTransaction.forCharge(
                        chargeRequest,
                        AbcAmount.of(new BigDecimal("10.000"))
                );

        assertThat(ledgerTransaction.eventType())
                .isEqualTo(LedgerEventType.CHARGE);
        assertThat(ledgerTransaction.idempotencyKey())
                .isEqualTo("charge-request:100");
        assertThat(ledgerTransaction.entries()).hasSize(2);
        assertThat(ledgerTransaction.isBalanced()).isTrue();
    }

    @Test
    void 증감액의_합계가_0이_아니면_원장_거래를_생성할_수_없다() {
        assertThatThrownBy(
                () -> new LedgerTransaction(
                        LedgerEventType.CHARGE,
                        "charge-request:100",
                        "CHARGE_REQUEST",
                        "100",
                        12345L,
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        List.of(
                                new LedgerEntry(
                                        LedgerAccountType.ABC_ISSUANCE,
                                        "SYSTEM",
                                        new BigDecimal("-9.000")
                                ),
                                new LedgerEntry(
                                        LedgerAccountType.ABC_BALANCE,
                                        "user-ledger-100",
                                        new BigDecimal("10.000")
                                )
                        )
                )
        ).isInstanceOfSatisfying(
                CoreException.class,
                exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.LEDGER_INVALID_TRANSACTION)
        );
    }

    @Test
    void 실행_선차감은_쓸_수_있는_잔액에서_예치로_옮기고_실행마다_키가_하나다() {
        UUID executionId = UUID.fromString("8a1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13");

        LedgerTransaction reserve = LedgerTransaction.forExecutionReserve(
                executionId, UserId.of(7L), AbcAmount.of(new BigDecimal("6")));

        assertThat(reserve.eventType()).isEqualTo(LedgerEventType.RESERVE);
        assertThat(reserve.idempotencyKey()).isEqualTo("execution-reserve:" + executionId);
        assertThat(reserve.referenceType()).isEqualTo("EXECUTION");
        assertThat(reserve.referenceId()).isEqualTo(executionId.toString());
        assertThat(reserve.entries())
                .extracting(LedgerEntry::accountType, LedgerEntry::accountReferenceId, LedgerEntry::amountDelta)
                .containsExactly(
                        tuple(LedgerAccountType.ABC_BALANCE, "7", new BigDecimal("-6.000000")),
                        tuple(LedgerAccountType.EXECUTION_ESCROW, "7", new BigDecimal("6.000000")));
        assertThat(reserve.isBalanced()).isTrue();
    }

    @Test
    void 실행_되돌리기는_예치를_쓸_수_있는_잔액으로_돌리고_선차감과_다른_키를_쓴다() {
        UUID executionId = UUID.fromString("8a1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13");

        LedgerTransaction release = LedgerTransaction.forExecutionRelease(
                executionId, UserId.of(7L), AbcAmount.of(new BigDecimal("6")));

        assertThat(release.eventType()).isEqualTo(LedgerEventType.REFUND);
        assertThat(release.idempotencyKey()).isEqualTo("execution-release:" + executionId);
        assertThat(release.entries())
                .extracting(LedgerEntry::accountType, LedgerEntry::amountDelta)
                .containsExactly(
                        tuple(LedgerAccountType.EXECUTION_ESCROW, new BigDecimal("-6.000000")),
                        tuple(LedgerAccountType.ABC_BALANCE, new BigDecimal("6.000000")));
        assertThat(release.isBalanced()).isTrue();
    }

    @Test
    void 끝난_실행의_환불은_예치를_쓸_수_있는_잔액으로_돌리고_되돌리기와_다른_키를_쓴다() {
        UUID executionId = UUID.fromString("8a1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13");

        LedgerTransaction refund = LedgerTransaction.forExecutionRefund(
                executionId, UserId.of(7L), AbcAmount.of(new BigDecimal("5.25")));

        assertThat(refund.eventType()).isEqualTo(LedgerEventType.REFUND);
        assertThat(refund.idempotencyKey()).isEqualTo("execution-refund:" + executionId);
        assertThat(refund.referenceType()).isEqualTo("EXECUTION");
        assertThat(refund.entries())
                .extracting(LedgerEntry::accountType, LedgerEntry::accountReferenceId, LedgerEntry::amountDelta)
                .containsExactly(
                        tuple(LedgerAccountType.EXECUTION_ESCROW, "7", new BigDecimal("-5.250000")),
                        tuple(LedgerAccountType.ABC_BALANCE, "7", new BigDecimal("5.250000")));
        assertThat(refund.isBalanced()).isTrue();
    }

    @Test
    void 끝난_실행의_정산은_쓴_만큼을_예치에서_빼_주인마다_정산금으로_옮긴다() {
        UUID executionId = UUID.fromString("8a1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13");
        Map<UserId, AbcAmount> payouts = new LinkedHashMap<>();
        payouts.put(UserId.of(8L), AbcAmount.of(new BigDecimal("1.5")));
        payouts.put(UserId.of(9L), AbcAmount.of(new BigDecimal("0.25")));

        LedgerTransaction settlement = LedgerTransaction.forExecutionSettlement(executionId, UserId.of(7L), payouts);

        assertThat(settlement.eventType()).isEqualTo(LedgerEventType.SETTLEMENT);
        assertThat(settlement.idempotencyKey()).isEqualTo("execution-settlement:" + executionId);
        assertThat(settlement.referenceType()).isEqualTo("EXECUTION");
        assertThat(settlement.referenceId()).isEqualTo(executionId.toString());
        assertThat(settlement.entries())
                .extracting(LedgerEntry::accountType, LedgerEntry::accountReferenceId, LedgerEntry::amountDelta)
                .containsExactly(
                        tuple(LedgerAccountType.EXECUTION_ESCROW, "7", new BigDecimal("-1.750000")),
                        tuple(LedgerAccountType.PAYOUT_BALANCE, "8", new BigDecimal("1.500000")),
                        tuple(LedgerAccountType.PAYOUT_BALANCE, "9", new BigDecimal("0.250000")));
        assertThat(settlement.isBalanced()).isTrue();
    }

    @Test
    void 자기_에이전트를_자기가_쓴_실행은_자기_예치에서_자기_정산금으로_옮긴다() {
        UUID executionId = UUID.fromString("8a1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13");

        LedgerTransaction settlement = LedgerTransaction.forExecutionSettlement(
                executionId, UserId.of(7L), Map.of(UserId.of(7L), AbcAmount.of(new BigDecimal("0.75"))));

        assertThat(settlement.entries())
                .extracting(LedgerEntry::accountType, LedgerEntry::accountReferenceId, LedgerEntry::amountDelta)
                .containsExactly(
                        tuple(LedgerAccountType.EXECUTION_ESCROW, "7", new BigDecimal("-0.750000")),
                        tuple(LedgerAccountType.PAYOUT_BALANCE, "7", new BigDecimal("0.750000")));
    }

    @Test
    void 플랫폼_탓으로_실패한_실행은_플랫폼_부담_계정에서_빼_정산금으로_옮긴다() {
        UUID executionId = UUID.fromString("8a1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13");
        Map<UserId, AbcAmount> payouts = new LinkedHashMap<>();
        payouts.put(UserId.of(8L), AbcAmount.of(new BigDecimal("1.5")));
        payouts.put(UserId.of(9L), AbcAmount.of(new BigDecimal("0.25")));

        LedgerTransaction settlement = LedgerTransaction.forPlatformCoveredSettlement(executionId, payouts);

        assertThat(settlement.eventType()).isEqualTo(LedgerEventType.SETTLEMENT);
        assertThat(settlement.idempotencyKey()).isEqualTo("execution-settlement:" + executionId);
        assertThat(settlement.entries())
                .extracting(LedgerEntry::accountType, LedgerEntry::accountReferenceId, LedgerEntry::amountDelta)
                .containsExactly(
                        tuple(LedgerAccountType.PLATFORM_EXPENSE, "PLATFORM", new BigDecimal("-1.750000")),
                        tuple(LedgerAccountType.PAYOUT_BALANCE, "8", new BigDecimal("1.500000")),
                        tuple(LedgerAccountType.PAYOUT_BALANCE, "9", new BigDecimal("0.250000")));
        assertThat(settlement.isBalanced()).isTrue();
    }

    @Test
    void 받을_주인이_없으면_정산_거래를_만들_수_없다() {
        assertThatThrownBy(() -> LedgerTransaction.forExecutionSettlement(
                UUID.randomUUID(), UserId.of(7L), Map.of()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LEDGER_INVALID_TRANSACTION));
    }

    @ParameterizedTest
    @EnumSource(LedgerEventType.class)
    void 어떤_거래_종류로도_한_사용자의_잔액을_다른_사용자의_잔액으로_옮길_수_없다(LedgerEventType eventType) {
        assertRejected(eventType,
                new LedgerEntry(LedgerAccountType.ABC_BALANCE, "7", new BigDecimal("-10")),
                new LedgerEntry(LedgerAccountType.ABC_BALANCE, "8", new BigDecimal("10")));
    }

    @Test
    void 선차감은_다른_사용자의_예치로_옮길_수_없다() {
        assertRejected(LedgerEventType.RESERVE,
                new LedgerEntry(LedgerAccountType.ABC_BALANCE, "7", new BigDecimal("-10")),
                new LedgerEntry(LedgerAccountType.EXECUTION_ESCROW, "8", new BigDecimal("10")));
    }

    @Test
    void 환불은_다른_사용자의_잔액으로_돌릴_수_없다() {
        assertRejected(LedgerEventType.REFUND,
                new LedgerEntry(LedgerAccountType.EXECUTION_ESCROW, "7", new BigDecimal("-10")),
                new LedgerEntry(LedgerAccountType.ABC_BALANCE, "8", new BigDecimal("10")));
    }

    @Test
    void 방향이_뒤집힌_선차감은_만들_수_없다() {
        assertRejected(LedgerEventType.RESERVE,
                new LedgerEntry(LedgerAccountType.EXECUTION_ESCROW, "7", new BigDecimal("-10")),
                new LedgerEntry(LedgerAccountType.ABC_BALANCE, "7", new BigDecimal("10")));
    }

    @Test
    void 정산은_예치가_아닌_쓸_수_있는_잔액에서_뺄_수_없다() {
        assertRejected(LedgerEventType.SETTLEMENT,
                new LedgerEntry(LedgerAccountType.ABC_BALANCE, "7", new BigDecimal("-10")),
                new LedgerEntry(LedgerAccountType.PAYOUT_BALANCE, "8", new BigDecimal("10")));
    }

    @Test
    void 정산은_공급자의_쓸_수_있는_잔액으로_넣을_수_없다() {
        assertRejected(LedgerEventType.SETTLEMENT,
                new LedgerEntry(LedgerAccountType.EXECUTION_ESCROW, "7", new BigDecimal("-10")),
                new LedgerEntry(LedgerAccountType.PAYOUT_BALANCE, "8", new BigDecimal("4")),
                new LedgerEntry(LedgerAccountType.ABC_BALANCE, "9", new BigDecimal("6")));
    }

    @Test
    void 충전은_발행_계정이_아닌_곳에서_ABC를_꺼낼_수_없다() {
        assertRejected(LedgerEventType.CHARGE,
                new LedgerEntry(LedgerAccountType.ABC_ISSUANCE, "8", new BigDecimal("-10")),
                new LedgerEntry(LedgerAccountType.ABC_BALANCE, "7", new BigDecimal("10")));
    }

    @Test
    void 빠지는_칸이_둘인_거래는_만들_수_없다() {
        assertRejected(LedgerEventType.SETTLEMENT,
                new LedgerEntry(LedgerAccountType.EXECUTION_ESCROW, "7", new BigDecimal("-6")),
                new LedgerEntry(LedgerAccountType.EXECUTION_ESCROW, "8", new BigDecimal("-4")),
                new LedgerEntry(LedgerAccountType.PAYOUT_BALANCE, "9", new BigDecimal("10")));
    }

    private void assertRejected(LedgerEventType eventType, LedgerEntry... entries) {
        assertThatThrownBy(() -> new LedgerTransaction(
                eventType, "key-1", "EXECUTION", "reference-1", null, null, List.of(entries)))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LEDGER_INVALID_TRANSACTION));
    }

    private ChargeRequest persistedPendingChargeRequest() {
        return ChargeRequest.restore(
                100L,
                "user-ledger-100",
                "0x1111111111111111111111111111111111111111",
                12345L,
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                DepositAmount.of(new BigDecimal("10.000000")),
                null,
                null,
                ChargeStatus.PENDING,
                null,
                null,
                0L
        );
    }
}
