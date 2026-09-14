package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTransactionTest {

    @Test
    void 충전_요청으로_균형이_맞는_원장_거래를_생성한다() {
        ChargeRequest chargeRequest = persistedPendingChargeRequest();

        LedgerTransaction ledgerTransaction =
                LedgerTransaction.forCharge(
                        chargeRequest,
                        new BigDecimal("10.000")
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

    private ChargeRequest persistedPendingChargeRequest() {
        return ChargeRequest.restore(
                100L,
                "user-ledger-100",
                "0x1111111111111111111111111111111111111111",
                12345L,
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                new BigDecimal("10.000000"),
                null,
                ChargeStatus.PENDING,
                null,
                null,
                0L
        );
    }
}
