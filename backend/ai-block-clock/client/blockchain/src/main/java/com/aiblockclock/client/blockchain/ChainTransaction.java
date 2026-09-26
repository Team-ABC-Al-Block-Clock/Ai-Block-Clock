package com.aiblockclock.client.blockchain;

import java.math.BigInteger;
import java.util.List;

// 거래 해시 하나로 체인에서 읽은 사실이다. 판정(누구의 입금인가, 금액이 맞는가)은 하지 않는다.
// 블록에 적히기 전(NOT_FOUND · PENDING)에는 블록 번호와 전송 기록이 없다.
public record ChainTransaction(
        ChainTransactionStatus status,
        Long chainId,
        BigInteger blockNumber,
        BigInteger latestBlockNumber,
        List<ChainTokenTransfer> tokenTransfers
) {
    public ChainTransaction {
        tokenTransfers = List.copyOf(tokenTransfers);
    }

    static ChainTransaction notFound() {
        return new ChainTransaction(ChainTransactionStatus.NOT_FOUND, null, null, null, List.of());
    }

    static ChainTransaction pending(Long chainId) {
        return new ChainTransaction(ChainTransactionStatus.PENDING, chainId, null, null, List.of());
    }
}
