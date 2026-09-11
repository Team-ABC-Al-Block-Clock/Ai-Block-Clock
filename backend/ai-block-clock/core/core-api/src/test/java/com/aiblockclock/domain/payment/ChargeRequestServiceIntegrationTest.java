package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.payment.ChargeNetworkPolicy;
import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class ChargeRequestServiceIntegrationTest {

    private static final ChargeNetworkPolicy NETWORK_POLICY =
            new ChargeNetworkPolicy(
                    12345L,
                    "0x2222222222222222222222222222222222222222",
                    "0x3333333333333333333333333333333333333333",
                    5L
            );

    @Autowired
    private ChargeRequestService chargeRequestService;

    @Test
    void 새로운_충전_요청을_PENDING_상태로_저장한다() {
        ChargeRequestEntity result =
                chargeRequestService.createPending(
                        "user-request-100",
                        "0x1111111111111111111111111111111111111111",
                        NETWORK_POLICY,
                        "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        new BigDecimal("10")
                );

        assertThat(result.getId())
                .isNotNull();

        assertThat(result.getStatus())
                .isEqualTo(ChargeStatus.PENDING);

        assertThat(result.getTransactionHash())
                .isEqualTo(
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                );

        assertThat(result.getRequestedUsdcAmount())
                .isEqualByComparingTo("10.000000");
    }

    @Test
    void 같은_사용자가_동일한_트랜잭션으로_재요청하면_기존_요청을_반환한다() {
        String transactionHash =
                "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

        ChargeRequestEntity first =
                chargeRequestService.createPending(
                        "user-request-200",
                        "0x4444444444444444444444444444444444444444",
                        NETWORK_POLICY,
                        transactionHash,
                        new BigDecimal("10")
                );

        ChargeRequestEntity second =
                chargeRequestService.createPending(
                        "user-request-200",
                        "0x4444444444444444444444444444444444444444",
                        NETWORK_POLICY,
                        transactionHash,
                        new BigDecimal("10")
                );

        assertThat(second.getId())
                .isEqualTo(first.getId());
    }

    @Test
    void 다른_사용자가_이미_사용된_트랜잭션으로_요청하면_차단한다() {
        String transactionHash =
                "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";

        chargeRequestService.createPending(
                "user-request-300",
                "0x5555555555555555555555555555555555555555",
                NETWORK_POLICY,
                transactionHash,
                new BigDecimal("10")
        );

        assertThatThrownBy(
                () -> chargeRequestService.createPending(
                        "user-request-400",
                        "0x6666666666666666666666666666666666666666",
                        NETWORK_POLICY,
                        transactionHash,
                        new BigDecimal("10")
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "이미 다른 사용자의 충전에 사용된 트랜잭션입니다."
                );
    }

    @Test
    void 본인의_충전_요청만_조회할_수_있다() {
        ChargeRequestEntity saved =
                chargeRequestService.createPending(
                        "user-request-500",
                        "0x7777777777777777777777777777777777777777",
                        NETWORK_POLICY,
                        "0xdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                        new BigDecimal("10")
                );

        ChargeRequestEntity found =
                chargeRequestService.getOwnedCharge(
                        saved.getId(),
                        "user-request-500"
                );

        assertThat(found.getId())
                .isEqualTo(saved.getId());

        assertThatThrownBy(
                () -> chargeRequestService.getOwnedCharge(
                        saved.getId(),
                        "other-user"
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "다른 사용자의 충전 요청은 조회할 수 없습니다."
                );
    }
}
