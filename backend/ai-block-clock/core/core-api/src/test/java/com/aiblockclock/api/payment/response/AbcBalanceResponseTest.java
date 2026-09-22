package com.aiblockclock.api.payment.response;

import com.aiblockclock.domain.payment.AbcBalance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AbcBalanceResponseTest {

    @Test
    void 잔액_도메인을_API_응답으로_변환한다() {
        AbcBalance abcBalance = AbcBalance.of(new BigDecimal("12.345"), new BigDecimal("3"));

        AbcBalanceResponse response = AbcBalanceResponse.from(abcBalance);

        assertThat(response.available()).isEqualByComparingTo("12.345");
        assertThat(response.reserved()).isEqualByComparingTo("3");
    }
}
