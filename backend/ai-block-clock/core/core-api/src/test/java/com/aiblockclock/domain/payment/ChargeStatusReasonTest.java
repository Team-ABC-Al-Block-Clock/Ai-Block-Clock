package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatusReason;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeStatusReasonTest {

    @Test
    void 요청이_거래와_어긋난_사유만_다시_확인할_수_있다() {
        assertThat(Arrays.stream(ChargeStatusReason.values()).filter(ChargeStatusReason::isRetryable))
                .containsExactlyInAnyOrder(
                        ChargeStatusReason.SENDER_WALLET_MISMATCH,
                        ChargeStatusReason.AMOUNT_MISMATCH,
                        ChargeStatusReason.VERIFICATION_TIMEOUT
                );
    }
}
