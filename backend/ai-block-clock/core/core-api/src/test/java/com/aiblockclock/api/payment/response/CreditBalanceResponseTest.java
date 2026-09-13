package com.aiblockclock.api.payment.response;

import com.aiblockclock.domain.payment.CreditBalance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CreditBalanceResponseTest {

    @Test
    void 잔액_도메인을_API_응답으로_변환한다() {
        CreditBalance creditBalance =
                CreditBalance.from(
                        new BigDecimal("12.345")
                );

        CreditBalanceResponse response =
                CreditBalanceResponse.from(creditBalance);

        assertThat(response.balance())
                .isEqualByComparingTo("12.345");
    }
}
