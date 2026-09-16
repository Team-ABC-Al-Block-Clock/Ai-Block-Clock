package com.aiblockclock.domain.payment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AbcConversionPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.000001", "1.000001", "10.123456", "10.000000", "1.1234560"})
    void 입금액을_반올림_없이_소수_6자리_ABC로_일대일_변환한다(String amount) {
        AbcConversionPolicy policy =
                new AbcConversionPolicy();

        AbcAmount abcAmount =
                policy.convert(
                        DepositAmount.of(new BigDecimal(amount))
                );

        assertThat(abcAmount.value())
                .isEqualByComparingTo(amount);
        assertThat(abcAmount.value().scale()).isEqualTo(6);
    }
}
