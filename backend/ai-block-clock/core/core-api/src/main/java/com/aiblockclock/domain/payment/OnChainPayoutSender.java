package com.aiblockclock.domain.payment;

import com.aiblockclock.client.blockchain.BlockchainClientException;
import com.aiblockclock.client.blockchain.SignedChainTransaction;
import com.aiblockclock.client.blockchain.SsafyChainClient;
import com.aiblockclock.client.blockchain.SsafyChainSigner;
import com.aiblockclock.client.blockchain.SsafyStoClient;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Optional;

// 출금을 보관 지갑에서 abcToken 으로 보내는 체인 쪽 일을 맡는다. 판정은 하지 않고 체인이 답한 사실만 돌려준다.
// 체인 · 토큰증권 API 에 닿지 못한 것은 "모름"으로 돌려 실패와 가른다(242 결과 판정 원칙).
@Component
@RequiredArgsConstructor
public class OnChainPayoutSender {
    // abcToken 의 decimals. 체인에 적힌 정수 10^18 이 토큰 1개(= ABC 1)다
    private static final int TOKEN_DECIMALS = 18;

    private final SsafyChainClient ssafyChainClient;
    private final SsafyStoClient ssafyStoClient;
    private final SsafyChainSigner ssafyChainSigner;
    private final ChargeProperties chargeProperties;

    public long chainId() {
        return chargeProperties.chainId();
    }

    // 서명할 지갑이 사용자가 충전해 넣은 보관 지갑이어야 한다. 키가 없거나 다른 지갑이면 지급을 켤 수 없다
    public void ensurePayerIsCustody() {
        Optional<String> payer = ssafyChainSigner.address();

        if (payer.isEmpty() || !payer.get().equalsIgnoreCase(chargeProperties.custodyAddress())) {
            throw new CoreException(ErrorType.WITHDRAWAL_PAYER_MISMATCH);
        }
    }

    // 잔액을 보고, 다음 거래 번호와 가스를 재서 서명한다. 체인에 보내지는 않는다 — 해시를 먼저 적어 두고 보내야 하기 때문이다
    public PayoutPreparation prepare(Withdrawal withdrawal) {
        BigInteger rawAmount = rawAmountOf(withdrawal);
        String custody = chargeProperties.custodyAddress();
        String token = chargeProperties.depositTokenContractAddress();

        try {
            if (ssafyStoClient.getBalance(token, custody).compareTo(rawAmount) < 0) {
                return PayoutPreparation.of(PayoutPreparation.Outcome.CUSTODY_BALANCE_SHORT);
            }

            BigInteger nonce = ssafyChainClient.readPendingNonce(custody);
            BigInteger gasLimit = ssafyChainClient.estimateTokenTransferGas(
                    custody, token, withdrawal.getWalletAddress(), rawAmount);
            SignedChainTransaction signed = ssafyChainSigner.signTokenTransfer(
                    chargeProperties.chainId(), token, withdrawal.getWalletAddress(), rawAmount, nonce, gasLimit);

            return PayoutPreparation.prepared(new PayoutTransaction(signed.transactionHash(), signed.rawTransaction()));
        } catch (BlockchainClientException exception) {
            return whenUnreachable(exception, PayoutPreparation.of(PayoutPreparation.Outcome.UNREACHABLE));
        }
    }

    // 노드에 닿아 받았다는 답이 오면 true 다. false 여도 거래가 퍼졌을 수 있어 부르는 쪽은 해시로 결과를 다시 본다
    public boolean submit(PayoutTransaction transaction) {
        try {
            ssafyChainClient.submitRawTransaction(transaction.rawTransaction());
            return true;
        } catch (BlockchainClientException exception) {
            return whenUnreachable(exception, false);
        }
    }

    public OnChainTransactionStatus readStatus(PayoutTransaction transaction) {
        try {
            return switch (ssafyChainClient.readTransaction(transaction.transactionHash()).status()) {
                case NOT_FOUND -> OnChainTransactionStatus.NOT_FOUND;
                case PENDING -> OnChainTransactionStatus.PENDING;
                case SUCCEEDED -> OnChainTransactionStatus.SUCCESS;
                case FAILED -> OnChainTransactionStatus.FAILED;
            };
        } catch (BlockchainClientException exception) {
            return whenUnreachable(exception, OnChainTransactionStatus.UNKNOWN);
        }
    }

    // 장부 금액은 소수 6자리라 10^18 을 곱하면 늘 정수다
    private BigInteger rawAmountOf(Withdrawal withdrawal) {
        return withdrawal.getAmount().value().movePointRight(TOKEN_DECIMALS).toBigIntegerExact();
    }

    // 노드가 답하지 않았거나 깨진 답을 준 것만 "모름"이다. 설정 · 주소 · 형식 오류는 다음 차례에도 같아서 드러낸다
    private <T> T whenUnreachable(BlockchainClientException exception, T unknown) {
        return switch (exception.getError()) {
            case REQUEST_FAILED, MALFORMED_RESPONSE -> unknown;
            case INVALID_CONFIGURATION -> throw new CoreException(ErrorType.WITHDRAWAL_PAYER_MISMATCH);
            // INVALID_DOCUMENT · TRANSACTION_REVERTED 는 등기부 문서 쓰기에서만 나와 지급에는 오지 않는다. 와도 실패가 확인된 것이다
            case INVALID_ADDRESS, INVALID_TRANSACTION_HASH, INVALID_TRANSACTION, INVALID_AMOUNT,
                 INVALID_DOCUMENT, TRANSACTION_REVERTED ->
                    throw new CoreException(ErrorType.WITHDRAWAL_PAYOUT_UNSENDABLE);
        };
    }
}
