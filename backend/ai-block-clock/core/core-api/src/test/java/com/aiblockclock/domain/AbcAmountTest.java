package com.aiblockclock.domain;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbcAmountTest {

    @Test
    void ABC_금액을_소수_6자리로_정규화한다() {
        AbcAmount amount = AbcAmount.of(
                new BigDecimal("1.1")
        );

        assertThat(amount.value())
                .isEqualByComparingTo("1.100000");
        assertThat(amount.value().scale()).isEqualTo(6);
    }

    @Test
    void 곱한_금액도_소수_6자리를_지킨다() {
        AbcAmount amount = AbcAmount.of(new BigDecimal("3.0"))
                .multiply(new BigDecimal("1.5"));

        assertThat(amount.value()).isEqualByComparingTo("4.500000");
        assertThat(amount.value().scale()).isEqualTo(6);
    }

    @Test
    void 곱해서_자릿수가_넘치면_반올림한다() {
        AbcAmount amount = AbcAmount.of(new BigDecimal("1.000001"))
                .multiply(new BigDecimal("1.5"));

        assertThat(amount.value()).isEqualByComparingTo("1.500002");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "1.0000001"})
    void 올바르지_않은_ABC_금액은_생성할_수_없다(String value) {
        BigDecimal amount = value == null
                ? null
                : new BigDecimal(value);

        assertThatThrownBy(() -> AbcAmount.of(amount))
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(ErrorType.CHARGE_INVALID_AMOUNT)
                );
    }
}
