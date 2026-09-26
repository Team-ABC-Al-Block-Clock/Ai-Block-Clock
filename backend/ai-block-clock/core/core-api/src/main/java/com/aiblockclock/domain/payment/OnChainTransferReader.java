package com.aiblockclock.domain.payment;

import com.aiblockclock.client.blockchain.BlockchainClientError;
import com.aiblockclock.client.blockchain.BlockchainClientException;
import com.aiblockclock.client.blockchain.ChainTokenTransfer;
import com.aiblockclock.client.blockchain.ChainTransaction;
import com.aiblockclock.client.blockchain.SsafyChainClient;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

// 충전 거래 해시 하나를 체인에서 읽어 검증기가 대조할 입금 한 건으로 바꾼다.
// 판정은 하지 않는다. 체인에 닿지 못한 것만 "모름"으로 돌려 실패와 구분한다.
@Component
@RequiredArgsConstructor
public class OnChainTransferReader {
    // abcToken 의 decimals. 체인에 적힌 정수 10^18 이 토큰 1개(= ABC 1)다
    private static final int DEPOSIT_TOKEN_DECIMALS = 18;

    private final SsafyChainClient ssafyChainClient;

    public OnChainTokenTransfer read(ChargeVerificationCriteria criteria) {
        String transactionHash = criteria.expectedTransactionHash();
        ChainTransaction transaction;

        try {
            transaction = ssafyChainClient.readTransaction(transactionHash);
        } catch (BlockchainClientException exception) {
            return whenUnreadable(exception, transactionHash);
        }

        return switch (transaction.status()) {
            case NOT_FOUND -> beforeBlock(OnChainTransactionStatus.NOT_FOUND, transaction, transactionHash);
            case PENDING -> beforeBlock(OnChainTransactionStatus.PENDING, transaction, transactionHash);
            case FAILED -> inBlock(OnChainTransactionStatus.FAILED, transaction, transactionHash, criteria);
            case SUCCEEDED -> inBlock(OnChainTransactionStatus.SUCCESS, transaction, transactionHash, criteria);
        };
    }

    // 해시 형식이 틀린 것은 체인에 있을 수 없는 거래라 "없음"으로 두어 대기 한도에 닫히게 한다.
    // 그 밖(연결 실패·노드 오류·깨진 응답)은 거래가 이미 성공했을 수도 있으니 모른다고만 한다.
    private OnChainTokenTransfer whenUnreadable(BlockchainClientException exception, String transactionHash) {
        if (exception.getError() == BlockchainClientError.INVALID_TRANSACTION_HASH) {
            return new OnChainTokenTransfer(OnChainTransactionStatus.NOT_FOUND, null, transactionHash,
                    null, null, null, null, 0L);
        }

        return OnChainTokenTransfer.unknown(transactionHash);
    }

    private OnChainTokenTransfer beforeBlock(
            OnChainTransactionStatus status,
            ChainTransaction transaction,
            String transactionHash
    ) {
        return new OnChainTokenTransfer(status, transaction.chainId(), transactionHash,
                null, null, null, null, 0L);
    }

    private OnChainTokenTransfer inBlock(
            OnChainTransactionStatus status,
            ChainTransaction transaction,
            String transactionHash,
            ChargeVerificationCriteria criteria
    ) {
        List<ChainTokenTransfer> transfers = transaction.tokenTransfers();
        List<ChainTokenTransfer> depositsToCustody = transfers.stream()
                .filter(transfer -> isDepositToken(transfer, criteria) && isToCustody(transfer, criteria))
                .toList();
        Optional<ChainTokenTransfer> representative = representativeOf(transfers, depositsToCustody, criteria);

        // 한 거래에 보관 지갑으로 가는 입금이 둘 이상이면 요청 금액과 한 건씩 맞출 수 없어 금액을 비워 거절되게 한다
        BigDecimal amount = depositsToCustody.size() == 1
                ? toTokenAmount(depositsToCustody.get(0).rawAmount())
                : null;

        return new OnChainTokenTransfer(
                status,
                transaction.chainId(),
                transactionHash,
                representative.map(ChainTokenTransfer::fromAddress).orElse(null),
                representative.map(ChainTokenTransfer::toAddress).orElse(null),
                representative.map(ChainTokenTransfer::tokenContractAddress).orElse(null),
                amount,
                confirmationsOf(transaction)
        );
    }

    // 검증기가 알맞은 거절 사유를 고르도록 가장 가까운 전송을 대표로 삼는다.
    // 보관 지갑으로 간 abcToken → 다른 곳으로 간 abcToken → 다른 토큰 순이다.
    private Optional<ChainTokenTransfer> representativeOf(
            List<ChainTokenTransfer> transfers,
            List<ChainTokenTransfer> depositsToCustody,
            ChargeVerificationCriteria criteria
    ) {
        if (!depositsToCustody.isEmpty()) {
            return Optional.of(depositsToCustody.get(0));
        }

        return transfers.stream()
                .filter(transfer -> isDepositToken(transfer, criteria))
                .findFirst()
                .or(() -> transfers.stream().findFirst());
    }

    private boolean isDepositToken(ChainTokenTransfer transfer, ChargeVerificationCriteria criteria) {
        return transfer.tokenContractAddress().equalsIgnoreCase(criteria.expectedDepositTokenContractAddress());
    }

    private boolean isToCustody(ChainTokenTransfer transfer, ChargeVerificationCriteria criteria) {
        return transfer.toAddress().equalsIgnoreCase(criteria.expectedCustodyAddress());
    }

    private BigDecimal toTokenAmount(BigInteger rawAmount) {
        return new BigDecimal(rawAmount).movePointLeft(DEPOSIT_TOKEN_DECIMALS);
    }

    // 거래가 적힌 블록 자신도 하나로 센다
    private long confirmationsOf(ChainTransaction transaction) {
        if (transaction.blockNumber() == null || transaction.latestBlockNumber() == null) {
            return 0L;
        }

        return transaction.latestBlockNumber().subtract(transaction.blockNumber()).add(BigInteger.ONE).longValueExact();
    }
}
