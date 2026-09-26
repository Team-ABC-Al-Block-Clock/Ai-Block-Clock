package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerEntryTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.000001", "-0.000001", "1.000001", "10.123456", "10.000"})
    void 원장_증감액을_값_손실_없이_소수_6자리로_표현한다(String amount) {
        LedgerEntry entry = new LedgerEntry(
                LedgerAccountType.ABC_BALANCE,
                "user-precision",
                new BigDecimal(amount)
        );

        assertThat(entry.amountDelta()).isEqualByComparingTo(amount);
        assertThat(entry.amountDelta().scale()).isEqualTo(6);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0000001", "-1.1234567"})
    void 지원하지_않는_증감액을_DB에서_반올림하기_전에_거부한다(String amount) {
        assertThatThrownBy(() -> new LedgerEntry(
                LedgerAccountType.ABC_BALANCE,
                "user-precision",
                new BigDecimal(amount)
        )).isInstanceOfSatisfying(CoreException.class,
                exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.LEDGER_INVALID_TRANSACTION));
    }
}
