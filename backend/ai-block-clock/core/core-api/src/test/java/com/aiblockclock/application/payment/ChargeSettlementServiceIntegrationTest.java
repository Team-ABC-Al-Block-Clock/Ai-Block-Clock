package com.aiblockclock.application.payment;

import com.aiblockclock.domain.payment.ChargeVerificationResult;
import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class ChargeSettlementServiceIntegrationTest {

    @Autowired
    private ChargeSettlementService settlementService;

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Test
    void 검증_결과가_WAITING이면_PENDING을_유지한다() {
        ChargeRequestEntity chargeRequest =
                chargeRequestRepository.saveAndFlush(
                        ChargeRequestEntity.pending(
                                "user-settlement-100",
                                "0x1111111111111111111111111111111111111111",
                                12345L,
                                "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                                new BigDecimal("10.000000")
                        )
                );

        ChargeRequestEntity result =
                settlementService.applyResult(
                        chargeRequest.getId(),
                        ChargeVerificationResult.waiting()
                );

        assertThat(result.getStatus())
                .isEqualTo(ChargeStatus.PENDING);

        assertThat(result.getCreditedAbcAmount())
                .isNull();

        assertThat(ledgerTransactionRepository.count())
                .isZero();
    }

    @Test
    void 검증_결과가_REJECTED이면_실패_사유와_함께_FAILED가_된다() {
        ChargeRequestEntity chargeRequest =
                chargeRequestRepository.saveAndFlush(
                        ChargeRequestEntity.pending(
                                "user-settlement-200",
                                "0x2222222222222222222222222222222222222222",
                                12345L,
                                "0xdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                                new BigDecimal("10.000000")
                        )
                );

        ChargeRequestEntity result =
                settlementService.applyResult(
                        chargeRequest.getId(),
                        ChargeVerificationResult.rejected(
                                ChargeStatusReason.AMOUNT_MISMATCH
                        )
                );

        assertThat(result.getStatus())
                .isEqualTo(ChargeStatus.FAILED);

        assertThat(result.getStatusReason())
                .isEqualTo(
                        ChargeStatusReason.AMOUNT_MISMATCH
                );

        assertThat(result.getCreditedAbcAmount())
                .isNull();

        assertThat(ledgerTransactionRepository.count())
                .isZero();
    }

    @Test
    void 검증_결과가_VERIFIED이면_ABC를_지급하고_CONFIRMED가_된다() {
        ChargeRequestEntity chargeRequest =
                chargeRequestRepository.saveAndFlush(
                        ChargeRequestEntity.pending(
                                "user-settlement-300",
                                "0x3333333333333333333333333333333333333333",
                                12345L,
                                "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                                new BigDecimal("10.000000")
                        )
                );

        ChargeRequestEntity result =
                settlementService.applyResult(
                        chargeRequest.getId(),
                        ChargeVerificationResult.verified()
                );

        ledgerTransactionRepository.flush();

        assertThat(result.getStatus())
                .isEqualTo(ChargeStatus.CONFIRMED);

        assertThat(result.getStatusReason())
                .isNull();

        assertThat(result.getCreditedAbcAmount())
                .isEqualByComparingTo("10.000");

        assertThat(result.getConfirmedAt())
                .isNotNull();

        assertThat(ledgerTransactionRepository.count())
                .isEqualTo(1);
    }

    @Test
    void 이미_확정된_충전은_다시_처리해도_중복_지급하지_않는다() {
        ChargeRequestEntity chargeRequest =
                chargeRequestRepository.saveAndFlush(
                        ChargeRequestEntity.pending(
                                "user-settlement-400",
                                "0x4444444444444444444444444444444444444444",
                                12345L,
                                "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                                new BigDecimal("10.000000")
                        )
                );

        ChargeRequestEntity firstResult =
                settlementService.applyResult(
                        chargeRequest.getId(),
                        ChargeVerificationResult.verified()
                );

        ChargeRequestEntity secondResult =
                settlementService.applyResult(
                        chargeRequest.getId(),
                        ChargeVerificationResult.verified()
                );

        ledgerTransactionRepository.flush();

        assertThat(firstResult.getStatus())
                .isEqualTo(ChargeStatus.CONFIRMED);

        assertThat(secondResult.getStatus())
                .isEqualTo(ChargeStatus.CONFIRMED);

        assertThat(secondResult.getCreditedAbcAmount())
                .isEqualByComparingTo("10.000");

        assertThat(secondResult.getConfirmedAt())
                .isEqualTo(firstResult.getConfirmedAt());

        assertThat(ledgerTransactionRepository.count())
                .isEqualTo(1);
    }
}
