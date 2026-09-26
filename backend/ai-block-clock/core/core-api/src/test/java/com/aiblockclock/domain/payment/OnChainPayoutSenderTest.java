package com.aiblockclock.domain.payment;

import com.aiblockclock.client.blockchain.BlockchainClientError;
import com.aiblockclock.client.blockchain.BlockchainClientException;
import com.aiblockclock.client.blockchain.ChainTransaction;
import com.aiblockclock.client.blockchain.ChainTransactionStatus;
import com.aiblockclock.client.blockchain.SignedChainTransaction;
import com.aiblockclock.client.blockchain.SsafyChainClient;
import com.aiblockclock.client.blockchain.SsafyChainSigner;
import com.aiblockclock.client.blockchain.SsafyStoClient;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.WithdrawalStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OnChainPayoutSenderTest {
    private static final String CUSTODY = "0x64e72A170a7a36cE09806190Ed02085fe23CB6B6";
    private static final String TOKEN = "0x56DC097C14836473f4Ad4BD675a3d01BE5e4C63c";
    private static final String RECEIVER = "0x1111111111111111111111111111111111111111";
    private static final PayoutTransaction TRANSACTION = new PayoutTransaction("0x" + "a".repeat(64), "0xf86c");

    private final SsafyChainClient chainClient = mock(SsafyChainClient.class);
    private final SsafyStoClient stoClient = mock(SsafyStoClient.class);
    private final SsafyChainSigner signer = mock(SsafyChainSigner.class);
    private OnChainPayoutSender sender;

    @BeforeEach
    void setUp() {
        ChargeProperties properties = new ChargeProperties(31221L, CUSTODY, TOKEN, 1L,
                Duration.ofSeconds(10), Duration.ofMinutes(5), Duration.ofMinutes(30), false);
        sender = new OnChainPayoutSender(chainClient, stoClient, signer, properties);
    }

    @Test
    void 서명할_지갑이_보관_지갑이면_대소문자가_달라도_통과한다() {
        given(signer.address()).willReturn(Optional.of(CUSTODY.toLowerCase()));

        sender.ensurePayerIsCustody();
    }

    @Test
    void 서명할_키가_없으면_지급을_켤_수_없다() {
        given(signer.address()).willReturn(Optional.empty());

        assertThatThrownBy(sender::ensurePayerIsCustody)
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_PAYER_MISMATCH));
    }

    @Test
    void 서명할_지갑이_보관_지갑이_아니면_지급을_켤_수_없다() {
        given(signer.address()).willReturn(Optional.of("0x9999999999999999999999999999999999999999"));

        assertThatThrownBy(sender::ensurePayerIsCustody)
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_PAYER_MISMATCH));
    }

    @Test
    void ABC_금액을_10의_18제곱을_곱한_정수로_바꿔_잰_가스와_다음_번호로_서명한다() {
        BigInteger raw = new BigInteger("12500001000000000000");
        given(stoClient.getBalance(TOKEN, CUSTODY)).willReturn(raw);
        given(chainClient.readPendingNonce(CUSTODY)).willReturn(BigInteger.valueOf(4));
        given(chainClient.estimateTokenTransferGas(CUSTODY, TOKEN, RECEIVER, raw)).willReturn(BigInteger.valueOf(60_000));
        given(signer.signTokenTransfer(31221L, TOKEN, RECEIVER, raw, BigInteger.valueOf(4), BigInteger.valueOf(60_000)))
                .willReturn(new SignedChainTransaction("0xf86c", "0x" + "a".repeat(64)));

        PayoutPreparation preparation = sender.prepare(approved("12.500001"));

        assertThat(preparation.outcome()).isEqualTo(PayoutPreparation.Outcome.PREPARED);
        assertThat(preparation.transaction()).isEqualTo(TRANSACTION);
    }

    @Test
    void 보관_지갑_잔액이_출금액보다_적으면_거래_번호도_묻지_않는다() {
        given(stoClient.getBalance(TOKEN, CUSTODY)).willReturn(BigInteger.TEN.pow(19).subtract(BigInteger.ONE));

        PayoutPreparation preparation = sender.prepare(approved("10"));

        assertThat(preparation.outcome()).isEqualTo(PayoutPreparation.Outcome.CUSTODY_BALANCE_SHORT);
        verify(chainClient, never()).readPendingNonce(anyString());
    }

    @Test
    void 토큰증권_API_에_닿지_못하면_준비하지_못한다() {
        given(stoClient.getBalance(TOKEN, CUSTODY))
                .willThrow(new BlockchainClientException(BlockchainClientError.REQUEST_FAILED, "timeout"));

        assertThat(sender.prepare(approved("10")).outcome()).isEqualTo(PayoutPreparation.Outcome.UNREACHABLE);
    }

    @Test
    void 서명할_키가_없으면_준비하다가_설정_오류로_알린다() {
        given(stoClient.getBalance(TOKEN, CUSTODY)).willReturn(BigInteger.TEN.pow(30));
        given(chainClient.readPendingNonce(CUSTODY)).willReturn(BigInteger.ZERO);
        given(chainClient.estimateTokenTransferGas(anyString(), anyString(), anyString(), any()))
                .willReturn(BigInteger.valueOf(60_000));
        given(signer.signTokenTransfer(anyLong(), anyString(), anyString(), any(), any(), any()))
                .willThrow(new BlockchainClientException(BlockchainClientError.INVALID_CONFIGURATION, "no key"));

        assertThatThrownBy(() -> sender.prepare(approved("10")))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_PAYER_MISMATCH));
    }

    @Test
    void 받는_지갑_주소가_틀리면_보낼_수_없는_출금으로_알린다() {
        given(stoClient.getBalance(TOKEN, CUSTODY)).willReturn(BigInteger.TEN.pow(30));
        given(chainClient.readPendingNonce(CUSTODY)).willReturn(BigInteger.ZERO);
        given(chainClient.estimateTokenTransferGas(anyString(), anyString(), anyString(), any()))
                .willThrow(new BlockchainClientException(BlockchainClientError.INVALID_ADDRESS, "bad address"));

        assertThatThrownBy(() -> sender.prepare(approved("10")))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_PAYOUT_UNSENDABLE));
    }

    @Test
    void 노드가_받으면_true_닿지_못하면_false_다() {
        assertThat(sender.submit(TRANSACTION)).isTrue();

        willThrow(new BlockchainClientException(BlockchainClientError.REQUEST_FAILED, "timeout"))
                .given(chainClient).submitRawTransaction("0xf86c");

        assertThat(sender.submit(TRANSACTION)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"NOT_FOUND, NOT_FOUND", "PENDING, PENDING", "SUCCEEDED, SUCCESS", "FAILED, FAILED"})
    void 체인이_답한_거래_상태를_그대로_옮긴다(ChainTransactionStatus chainStatus, OnChainTransactionStatus expected) {
        given(chainClient.readTransaction(TRANSACTION.transactionHash()))
                .willReturn(new ChainTransaction(chainStatus, 31221L, null, null, List.of()));

        assertThat(sender.readStatus(TRANSACTION)).isEqualTo(expected);
    }

    @Test
    void 체인에_닿지_못하면_거래_상태를_모른다고_한다() {
        given(chainClient.readTransaction(TRANSACTION.transactionHash()))
                .willThrow(new BlockchainClientException(BlockchainClientError.REQUEST_FAILED, "timeout"));

        assertThat(sender.readStatus(TRANSACTION)).isEqualTo(OnChainTransactionStatus.UNKNOWN);
    }

    private Withdrawal approved(String amount) {
        return Withdrawal.restore(31L, WithdrawalRequestId.of(UUID.randomUUID()), UserId.of(7L),
                AbcAmount.of(new BigDecimal(amount)), RECEIVER, WithdrawalStatus.APPROVED,
                null, UserId.of(1L), null, null, null, null, null);
    }
}
