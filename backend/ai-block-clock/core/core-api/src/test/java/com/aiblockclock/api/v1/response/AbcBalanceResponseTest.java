package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.payment.AbcBalance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AbcBalanceResponseTest {

    @Test
    void 잔액_도메인을_문자열_금액_응답으로_변환한다() {
        AbcBalance abcBalance = AbcBalance.of(new BigDecimal("12.345"), new BigDecimal("3"));

        AbcBalanceResponse response = AbcBalanceResponse.from(abcBalance);

        assertThat(response.availableAmount()).isEqualTo("12.345000");
        assertThat(response.reservedAmount()).isEqualTo("3.000000");
    }
}
