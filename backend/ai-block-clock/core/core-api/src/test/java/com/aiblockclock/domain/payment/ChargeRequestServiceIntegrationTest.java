package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
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

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @AfterEach
    void cleanUp() {
        chargeRequestRepository.findAll().stream()
                .filter(request -> request.getUserReferenceId().startsWith("user-request-"))
                .forEach(chargeRequestRepository::delete);
    }

    @Test
    void 새로운_충전_요청을_PENDING_상태로_저장한다() {
        ChargeRequest result =
                chargeRequestService.createChargeRequest(
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

        ChargeRequest first =
                chargeRequestService.createChargeRequest(
                        "user-request-200",
                        "0x4444444444444444444444444444444444444444",
                        NETWORK_POLICY,
                        transactionHash,
                        new BigDecimal("10")
                );

        ChargeRequest second =
                chargeRequestService.createChargeRequest(
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

        chargeRequestService.createChargeRequest(
                "user-request-300",
                "0x5555555555555555555555555555555555555555",
                NETWORK_POLICY,
                transactionHash,
                new BigDecimal("10")
        );

        assertThatThrownBy(
                () -> chargeRequestService.createChargeRequest(
                        "user-request-400",
                        "0x6666666666666666666666666666666666666666",
                        NETWORK_POLICY,
                        transactionHash,
                        new BigDecimal("10")
                )
        )
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(
                                        ErrorType.CHARGE_TRANSACTION_ALREADY_USED
                                )
                );
    }

    @Test
    void 본인의_충전_요청만_조회할_수_있다() {
        ChargeRequest saved =
                chargeRequestService.createChargeRequest(
                        "user-request-500",
                        "0x7777777777777777777777777777777777777777",
                        NETWORK_POLICY,
                        "0xdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                        new BigDecimal("10")
                );

        ChargeRequest found =
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
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(
                                        ErrorType.CHARGE_REQUEST_FORBIDDEN
                                )
                );
    }

    @Test
    void 존재하지_않는_충전_요청은_조회할_수_없다() {
        assertThatThrownBy(
                () -> chargeRequestService.getOwnedCharge(
                        Long.MAX_VALUE,
                        "user-request-600"
                )
        ).isInstanceOfSatisfying(
                CoreException.class,
                exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.CHARGE_REQUEST_NOT_FOUND)
        );
    }
}
