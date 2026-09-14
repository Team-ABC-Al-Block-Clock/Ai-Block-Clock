package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
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
class ChargeConfirmationManagerIntegrationTest {

    @Autowired
    private ChargeConfirmationManager chargeConfirmationManager;

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Test
    void 정상_충전을_원장에_기록하고_CONFIRMED로_변경한다() {
        ChargeRequestEntity chargeRequest =
                savePendingChargeRequest(
                        "user-confirmation-100",
                        "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
                );

        ChargeRequest confirmedChargeRequest =
                chargeConfirmationManager.confirm(
                        chargeRequest.getId(),
                        new BigDecimal("10.000")
                );

        ledgerTransactionRepository.flush();

        assertThat(confirmedChargeRequest.getStatus())
                .isEqualTo(ChargeStatus.CONFIRMED);
        assertThat(confirmedChargeRequest.getCreditedAbcAmount())
                .isEqualByComparingTo("10.000");
        assertThat(confirmedChargeRequest.getConfirmedAt())
                .isNotNull();
        assertThat(ledgerTransactionRepository.count())
                .isEqualTo(1);
    }

    @Test
    void 이미_확정된_충전을_다시_확정해도_중복_지급하지_않는다() {
        ChargeRequestEntity chargeRequest =
                savePendingChargeRequest(
                        "user-confirmation-200",
                        "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
                );

        ChargeRequest firstConfirmedChargeRequest =
                chargeConfirmationManager.confirm(
                        chargeRequest.getId(),
                        new BigDecimal("10.000")
                );

        ChargeRequest secondConfirmedChargeRequest =
                chargeConfirmationManager.confirm(
                        chargeRequest.getId(),
                        new BigDecimal("10.000")
                );

        ledgerTransactionRepository.flush();

        assertThat(secondConfirmedChargeRequest.getStatus())
                .isEqualTo(ChargeStatus.CONFIRMED);
        assertThat(secondConfirmedChargeRequest.getCreditedAbcAmount())
                .isEqualByComparingTo("10.000");
        assertThat(secondConfirmedChargeRequest.getConfirmedAt())
                .isEqualTo(
                        firstConfirmedChargeRequest.getConfirmedAt()
                );
        assertThat(ledgerTransactionRepository.count())
                .isEqualTo(1);
    }

    private ChargeRequestEntity savePendingChargeRequest(
            String userReferenceId,
            String transactionHash
    ) {
        return chargeRequestRepository.saveAndFlush(
                ChargeRequestEntity.pending(
                        userReferenceId,
                        "0x3333333333333333333333333333333333333333",
                        12345L,
                        transactionHash,
                        new BigDecimal("10.000000")
                )
        );
    }
}
