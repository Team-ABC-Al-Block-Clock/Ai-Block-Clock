package com.aiblockclock.storage.payment;
import com.aiblockclock.enums.payment.ChargeStatusReason;

import com.aiblockclock.enums.payment.ChargeStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class ChargeRequestIntegrationTest {

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @Test
    void 충전_요청은_PENDING_상태로_저장된다() {
        ChargeRequestEntity chargeRequest =
                ChargeRequestEntity.pending(
                        "user-100",
                        "0x1111111111111111111111111111111111111111",
                        12345L,
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        new BigDecimal("10.000000")
                );

        ChargeRequestEntity saved =
                chargeRequestRepository.saveAndFlush(chargeRequest);

        ChargeRequestEntity found =
                chargeRequestRepository.findById(saved.getId())
                        .orElseThrow();

        assertThat(found.getUserReferenceId())
                .isEqualTo("user-100");
        assertThat(found.getSenderWalletAddress())
                .isEqualTo("0x1111111111111111111111111111111111111111");
        assertThat(found.getChainId())
                .isEqualTo(12345L);
        assertThat(found.getTransactionHash())
                .isEqualTo(
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                );
        assertThat(found.getRequestedUsdcAmount())
                .isEqualByComparingTo("10.000000");

        assertThat(found.getStatus())
                .isEqualTo(ChargeStatus.PENDING);
        assertThat(found.getStatusReason())
                .isNull();
        assertThat(found.getCreditedAbcAmount())
                .isNull();
        assertThat(found.getConfirmedAt())
                .isNull();
    }

    @Test
    void 동일한_체인과_트랜잭션_해시는_중복_저장되지_않는다() {
        Long chainId = 12345L;
        String transactionHash =
                "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

        ChargeRequestEntity firstRequest =
                ChargeRequestEntity.pending(
                        "user-100",
                        "0x1111111111111111111111111111111111111111",
                        chainId,
                        transactionHash,
                        new BigDecimal("10.000000")
                );

        ChargeRequestEntity duplicateRequest =
                ChargeRequestEntity.pending(
                        "user-200",
                        "0x2222222222222222222222222222222222222222",
                        chainId,
                        transactionHash,
                        new BigDecimal("20.000000")
                );

        ChargeRequestEntity saved =
                chargeRequestRepository.saveAndFlush(firstRequest);

        ChargeRequestEntity found =
                chargeRequestRepository
                        .findByChainIdAndTransactionHash(
                                chainId,
                                transactionHash
                        )
                        .orElseThrow();

        assertThat(found.getId())
                .isEqualTo(saved.getId());

        assertThatThrownBy(
                () -> chargeRequestRepository.saveAndFlush(duplicateRequest)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 충전_요청_USDC_수량은_0보다_커야_한다() {
        ChargeRequestEntity invalidRequest =
                ChargeRequestEntity.pending(
                        "user-300",
                        "0x3333333333333333333333333333333333333333",
                        12345L,
                        "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                        new BigDecimal("0.000000")
                );

        assertThatThrownBy(
                () -> chargeRequestRepository.saveAndFlush(invalidRequest)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 대기_중인_충전_요청을_확정하면_CONFIRMED_상태가_된다() {
        ChargeRequestEntity chargeRequest =
                ChargeRequestEntity.pending(
                        "user-400",
                        "0x4444444444444444444444444444444444444444",
                        12345L,
                        "0xdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                        new BigDecimal("10.000000")
                );

        LocalDateTime confirmedAt =
                LocalDateTime.of(2026, 9, 10, 15, 0);

        chargeRequest.confirm(
                new BigDecimal("100.000"),
                confirmedAt
        );

        ChargeRequestEntity saved =
                chargeRequestRepository.saveAndFlush(chargeRequest);

        assertThat(saved.getStatus())
                .isEqualTo(ChargeStatus.CONFIRMED);

        assertThat(saved.getCreditedAbcAmount())
                .isEqualByComparingTo("100.000");

        assertThat(saved.getConfirmedAt())
                .isEqualTo(confirmedAt);

        assertThat(saved.getStatusReason())
                .isNull();
    }

    @Test
    void 대기_중인_충전_요청을_실패_처리하면_사유와_함께_FAILED가_된다() {
        ChargeRequestEntity chargeRequest =
                ChargeRequestEntity.pending(
                        "user-500",
                        "0x5555555555555555555555555555555555555555",
                        12345L,
                        "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                        new BigDecimal("10.000000")
                );

        chargeRequest.fail(
                ChargeStatusReason.AMOUNT_MISMATCH
        );

        ChargeRequestEntity saved =
                chargeRequestRepository.saveAndFlush(chargeRequest);

        assertThat(saved.getStatus())
                .isEqualTo(ChargeStatus.FAILED);

        assertThat(saved.getStatusReason())
                .isEqualTo(ChargeStatusReason.AMOUNT_MISMATCH);

        assertThat(saved.getCreditedAbcAmount())
                .isNull();

        assertThat(saved.getConfirmedAt())
                .isNull();
    }

    @Test
    void 충전_요청을_여러_번_취소해도_CANCELED가_유지된다() {
        ChargeRequestEntity chargeRequest =
                ChargeRequestEntity.pending(
                        "user-600",
                        "0x6666666666666666666666666666666666666666",
                        12345L,
                        "0x9999999999999999999999999999999999999999999999999999999999999999",
                        new BigDecimal("10.000000")
                );

        chargeRequest.cancel();
        chargeRequest.cancel();

        ChargeRequestEntity saved =
                chargeRequestRepository.saveAndFlush(chargeRequest);

        assertThat(saved.getStatus())
                .isEqualTo(ChargeStatus.CANCELED);

        assertThat(saved.getStatusReason())
                .isEqualTo(ChargeStatusReason.USER_CANCELED);

        assertThat(saved.getCreditedAbcAmount())
                .isNull();

        assertThat(saved.getConfirmedAt())
                .isNull();
    }
}
