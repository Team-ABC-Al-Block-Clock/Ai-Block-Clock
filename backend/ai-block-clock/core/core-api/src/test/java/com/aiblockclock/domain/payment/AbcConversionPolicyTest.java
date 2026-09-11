package com.aiblockclock.domain.payment;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.assertj.core.api.Assertions.assertThat;

class AbcConversionPolicyTest {

    @Test
    void USDC를_ABC로_일대일_변환한다() {
        AbcConversionPolicy policy =
                new AbcConversionPolicy();

        BigDecimal abcAmount =
                policy.convert(
                        new BigDecimal("10.000000")
                );

        assertThat(abcAmount)
                .isEqualByComparingTo("10.000");
    }

    @Test
    void ABC가_지원하지_않는_소수점은_자동으로_반올림하지_않는다() {
        AbcConversionPolicy policy =
                new AbcConversionPolicy();

        assertThatThrownBy(
                () -> policy.convert(
                        new BigDecimal("10.123456")
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
