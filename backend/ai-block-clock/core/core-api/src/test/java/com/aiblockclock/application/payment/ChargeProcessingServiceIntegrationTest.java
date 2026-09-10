package com.aiblockclock.application.payment;

import com.aiblockclock.domain.payment.ChargeNetworkPolicy;
import com.aiblockclock.domain.payment.OnChainTransactionStatus;
import com.aiblockclock.domain.payment.OnChainUsdcTransfer;
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
class ChargeProcessingServiceIntegrationTest {

    @Autowired
    private ChargeProcessingService processingService;

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Test
    void 정상적인_온체인_USDC_거래를_검증하고_ABC_충전을_확정한다() {
        ChargeRequestEntity chargeRequest =
                chargeRequestRepository.saveAndFlush(
                        ChargeRequestEntity.pending(
                                "user-processing-100",
                                "0x1111111111111111111111111111111111111111",
                                12345L,
                                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                new BigDecimal("10.000000")
                        )
                );

        ChargeNetworkPolicy networkPolicy =
                new ChargeNetworkPolicy(
                        12345L,
                        "0x2222222222222222222222222222222222222222",
                        "0x3333333333333333333333333333333333333333",
                        5L
                );

        OnChainUsdcTransfer transfer =
                new OnChainUsdcTransfer(
                        OnChainTransactionStatus.SUCCESS,
                        12345L,
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "0x1111111111111111111111111111111111111111",
                        "0x2222222222222222222222222222222222222222",
                        "0x3333333333333333333333333333333333333333",
                        new BigDecimal("10.000000"),
                        5L
                );

        ChargeRequestEntity result =
                processingService.process(
                        chargeRequest.getId(),
                        transfer,
                        networkPolicy
                );

        ledgerTransactionRepository.flush();

        assertThat(result.getStatus())
                .isEqualTo(ChargeStatus.CONFIRMED);

        assertThat(result.getCreditedAbcAmount())
                .isEqualByComparingTo("10.000");

        assertThat(result.getConfirmedAt())
                .isNotNull();

        assertThat(ledgerTransactionRepository.count())
                .isEqualTo(1);
    }

    @Test
    void 충전_요청의_체인_ID가_허용된_체인과_다르면_FAILED가_된다() {
        ChargeRequestEntity chargeRequest =
                chargeRequestRepository.saveAndFlush(
                        ChargeRequestEntity.pending(
                                "user-processing-200",
                                "0x2222222222222222222222222222222222222222",
                                99999L,
                                "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                                new BigDecimal("10.000000")
                        )
                );

        ChargeNetworkPolicy networkPolicy =
                new ChargeNetworkPolicy(
                        12345L,
                        "0x2222222222222222222222222222222222222222",
                        "0x3333333333333333333333333333333333333333",
                        5L
                );

        OnChainUsdcTransfer transfer =
                new OnChainUsdcTransfer(
                        OnChainTransactionStatus.SUCCESS,
                        12345L,
                        "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        "0x2222222222222222222222222222222222222222",
                        "0x2222222222222222222222222222222222222222",
                        "0x3333333333333333333333333333333333333333",
                        new BigDecimal("10.000000"),
                        5L
                );

        ChargeRequestEntity result =
                processingService.process(
                        chargeRequest.getId(),
                        transfer,
                        networkPolicy
                );

        assertThat(result.getStatus())
                .isEqualTo(ChargeStatus.FAILED);

        assertThat(result.getStatusReason())
                .isEqualTo(
                        ChargeStatusReason.CHAIN_ID_MISMATCH
                );

        assertThat(ledgerTransactionRepository.count())
                .isZero();
    }
}
