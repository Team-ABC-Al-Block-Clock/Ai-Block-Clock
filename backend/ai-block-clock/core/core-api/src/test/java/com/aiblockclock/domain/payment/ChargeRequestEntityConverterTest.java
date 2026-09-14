package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeRequestEntityConverterTest {

    @Test
    void 새로운_도메인_충전_요청을_저장_엔티티로_변환한다() {
        ChargeRequest chargeRequest = createPendingChargeRequest();

        ChargeRequestEntity entity =
                ChargeRequestEntityConverter.toNewEntity(
                        chargeRequest
                );

        assertThat(entity.getUserReferenceId())
                .isEqualTo(chargeRequest.getUserReferenceId());
        assertThat(entity.getTransactionHash())
                .isEqualTo(chargeRequest.getTransactionHash());
        assertThat(entity.getRequestedUsdcAmount())
                .isEqualByComparingTo(
                        chargeRequest.getRequestedUsdcAmount()
                );
        assertThat(entity.getStatus())
                .isEqualTo(ChargeStatus.PENDING);
    }

    @Test
    void 저장_엔티티를_도메인_충전_요청으로_변환한다() {
        ChargeRequestEntity entity =
                ChargeRequestEntity.pending(
                        "user-request-200",
                        "0x2222222222222222222222222222222222222222",
                        12345L,
                        "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        new BigDecimal("20.000000")
                );

        ChargeRequest chargeRequest =
                ChargeRequestEntityConverter.toDomain(entity);

        assertThat(chargeRequest.getUserReferenceId())
                .isEqualTo(entity.getUserReferenceId());
        assertThat(chargeRequest.getChainId())
                .isEqualTo(entity.getChainId());
        assertThat(chargeRequest.getTransactionHash())
                .isEqualTo(entity.getTransactionHash());
        assertThat(chargeRequest.getStatus())
                .isEqualTo(ChargeStatus.PENDING);
    }

    private ChargeRequest createPendingChargeRequest() {
        return ChargeRequest.pending(
                "user-request-100",
                "0x1111111111111111111111111111111111111111",
                12345L,
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                new BigDecimal("10.000000")
        );
    }
}
