package com.aiblockclock.domain.payment;

import com.aiblockclock.client.blockchain.BlockchainClientError;
import com.aiblockclock.client.blockchain.BlockchainClientException;
import com.aiblockclock.client.blockchain.SsafyChainClient;
import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static com.aiblockclock.domain.payment.ChargeChainFixtures.CHAIN_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@Tag("integration")
@SpringBootTest(properties = {
        ChargeChainFixtures.CHAIN_ID_PROPERTY,
        ChargeChainFixtures.CUSTODY_PROPERTY,
        ChargeChainFixtures.TOKEN_PROPERTY,
        ChargeChainFixtures.CONFIRMATIONS_PROPERTY
})
@Transactional
class ChargeProcessingServiceIntegrationTest {

    private static final String SENDER = "0x1111111111111111111111111111111111111111";

    @Autowired
    private ChargeProcessingService processingService;

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @MockitoBean
    private SsafyChainClient ssafyChainClient;

    @Test
    void 보관_지갑으로_들어온_abcToken을_확인하고_같은_수만큼_ABC를_적립한다() {
        ChargeRequestEntity chargeRequest = savePending(CHAIN_ID, "a", "10.000000");
        given(ssafyChainClient.readTransaction(chargeRequest.getTransactionHash()))
                .willReturn(ChargeChainFixtures.deposited(SENDER, new BigDecimal("10")));

        ChargeProcessResult result = processingService.process(chargeRequest.getId());

        ledgerTransactionRepository.flush();

        assertThat(result.chainReached()).isTrue();
        assertThat(result.chargeRequest().getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
        assertThat(result.chargeRequest().getCreditedAbcAmount().value()).isEqualByComparingTo("10");
        assertThat(result.chargeRequest().getConfirmedAt()).isNotNull();
        assertThat(ledgerTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    void 충전_요청의_체인_ID가_허용된_체인과_다르면_체인에_묻지_않고_실패로_닫는다() {
        ChargeRequestEntity chargeRequest = savePending(99999L, "b", "10.000000");

        ChargeProcessResult result = processingService.process(chargeRequest.getId());

        assertThat(result.chargeRequest().getStatus()).isEqualTo(ChargeStatus.FAILED);
        assertThat(result.chargeRequest().getStatusReason()).isEqualTo(ChargeStatusReason.CHAIN_ID_MISMATCH);
        assertThat(ledgerTransactionRepository.count()).isZero();
    }

    @Test
    void 거래가_아직_블록에_적히지_않았으면_확인_중으로_남긴다() {
        ChargeRequestEntity chargeRequest = savePending(CHAIN_ID, "c", "10.000000");
        given(ssafyChainClient.readTransaction(chargeRequest.getTransactionHash()))
                .willReturn(ChargeChainFixtures.notYetInBlock());

        ChargeProcessResult result = processingService.process(chargeRequest.getId());

        assertThat(result.chainReached()).isTrue();
        assertThat(result.chargeRequest().getStatus()).isEqualTo(ChargeStatus.PENDING);
        assertThat(result.chargeRequest().getCreditedAbcAmount()).isNull();
        assertThat(ledgerTransactionRepository.count()).isZero();
    }

    @Test
    void 거래가_되돌려졌으면_실패_사유를_남긴다() {
        ChargeRequestEntity chargeRequest = savePending(CHAIN_ID, "d", "10.000000");
        given(ssafyChainClient.readTransaction(chargeRequest.getTransactionHash()))
                .willReturn(ChargeChainFixtures.reverted());

        ChargeProcessResult result = processingService.process(chargeRequest.getId());

        assertThat(result.chargeRequest().getStatus()).isEqualTo(ChargeStatus.FAILED);
        assertThat(result.chargeRequest().getStatusReason()).isEqualTo(ChargeStatusReason.ONCHAIN_TRANSACTION_FAILED);
        assertThat(ledgerTransactionRepository.count()).isZero();
    }

    @Test
    void 보낸_금액이_요청_금액과_다르면_적립하지_않고_실패로_닫는다() {
        ChargeRequestEntity chargeRequest = savePending(CHAIN_ID, "e", "10.000000");
        given(ssafyChainClient.readTransaction(chargeRequest.getTransactionHash()))
                .willReturn(ChargeChainFixtures.deposited(SENDER, new BigDecimal("5")));

        ChargeProcessResult result = processingService.process(chargeRequest.getId());

        assertThat(result.chargeRequest().getStatus()).isEqualTo(ChargeStatus.FAILED);
        assertThat(result.chargeRequest().getStatusReason()).isEqualTo(ChargeStatusReason.AMOUNT_MISMATCH);
        assertThat(ledgerTransactionRepository.count()).isZero();
    }

    @Test
    void 로그인_지갑이_아닌_지갑이_보낸_거래면_실패로_닫는다() {
        ChargeRequestEntity chargeRequest = savePending(CHAIN_ID, "f", "10.000000");
        given(ssafyChainClient.readTransaction(chargeRequest.getTransactionHash()))
                .willReturn(ChargeChainFixtures.deposited("0x9999999999999999999999999999999999999999",
                        new BigDecimal("10")));

        ChargeProcessResult result = processingService.process(chargeRequest.getId());

        assertThat(result.chargeRequest().getStatus()).isEqualTo(ChargeStatus.FAILED);
        assertThat(result.chargeRequest().getStatusReason()).isEqualTo(ChargeStatusReason.SENDER_WALLET_MISMATCH);
    }

    @Test
    void 체인에_닿지_못하면_실패로_단정하지_않고_확인_중으로_남긴다() {
        ChargeRequestEntity chargeRequest = savePending(CHAIN_ID, "1", "10.000000");
        given(ssafyChainClient.readTransaction(chargeRequest.getTransactionHash()))
                .willThrow(new BlockchainClientException(BlockchainClientError.REQUEST_FAILED, "timeout"));

        ChargeProcessResult result = processingService.process(chargeRequest.getId());

        assertThat(result.chainReached()).isFalse();
        assertThat(result.chargeRequest().getStatus()).isEqualTo(ChargeStatus.PENDING);
        assertThat(ledgerTransactionRepository.count()).isZero();
    }

    @Test
    void 대기_중인_요청만_먼저_들어온_순서로_읽는다() {
        ChargeRequestEntity first = savePending(CHAIN_ID, "2", "1.000000");
        ChargeRequestEntity second = savePending(CHAIN_ID, "3", "1.000000");
        ChargeRequestEntity closed = savePending(99999L, "4", "1.000000");
        processingService.process(closed.getId());

        assertThat(processingService.readPendingChargeIds())
                .containsSubsequence(first.getId(), second.getId())
                .doesNotContain(closed.getId());
    }

    private ChargeRequestEntity savePending(long chainId, String hashDigit, String amount) {
        return chargeRequestRepository.saveAndFlush(
                ChargeRequestEntity.pending(
                        "user-processing-" + hashDigit,
                        SENDER,
                        chainId,
                        "0x" + hashDigit.repeat(64),
                        new BigDecimal(amount)
                )
        );
    }
}
