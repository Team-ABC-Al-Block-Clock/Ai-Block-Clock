package com.aiblockclock.domain.payment;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepositAmountTest {

    @Test
    void 입금_금액을_소수_6자리로_정규화한다() {
        DepositAmount amount = DepositAmount.of(
                new BigDecimal("1.1")
        );

        assertThat(amount.value())
                .isEqualByComparingTo("1.100000");
        assertThat(amount.value().scale()).isEqualTo(6);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "1.0000001"})
    void 올바르지_않은_입금_금액은_생성할_수_없다(String value) {
        BigDecimal amount = value == null
                ? null
                : new BigDecimal(value);

        assertThatThrownBy(() -> DepositAmount.of(amount))
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(ErrorType.CHARGE_INVALID_AMOUNT)
                );
    }
}
