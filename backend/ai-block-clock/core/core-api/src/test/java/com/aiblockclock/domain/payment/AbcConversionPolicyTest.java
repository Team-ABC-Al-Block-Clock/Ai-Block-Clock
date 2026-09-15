package com.aiblockclock.domain.payment;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.assertj.core.api.Assertions.assertThat;

class AbcConversionPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.000001", "1.000001", "10.123456", "10.000000", "1.1234560"})
    void USDC를_반올림_없이_소수_6자리_ABC로_일대일_변환한다(String amount) {
        AbcConversionPolicy policy =
                new AbcConversionPolicy();

        BigDecimal abcAmount =
                policy.convert(
                        new BigDecimal(amount)
                );

        assertThat(abcAmount)
                .isEqualByComparingTo(amount);
        assertThat(abcAmount.scale()).isEqualTo(6);
    }

    @Test
    void ABC가_지원하지_않는_소수점은_자동으로_반올림하지_않는다() {
        AbcConversionPolicy policy =
                new AbcConversionPolicy();

        assertThatThrownBy(
                () -> policy.convert(
                        new BigDecimal("10.1234567")
                )
        ).isInstanceOfSatisfying(
                CoreException.class,
                exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.CHARGE_INVALID_AMOUNT)
        );
    }

    @Test
    void USDC_수량이_0_이하면_변환하지_않는다() {
        AbcConversionPolicy policy =
                new AbcConversionPolicy();

        assertThatThrownBy(
                () -> policy.convert(BigDecimal.ZERO)
        ).isInstanceOfSatisfying(
                CoreException.class,
                exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.CHARGE_INVALID_AMOUNT)
        );
    }
}
