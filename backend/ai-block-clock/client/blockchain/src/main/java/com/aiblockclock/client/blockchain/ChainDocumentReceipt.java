package com.aiblockclock.client.blockchain;

import java.math.BigInteger;

// 문서를 적은 거래가 블록에 들어가 성공한 것까지 확인한 영수증이다. 되돌려진 거래는 영수증으로 돌려주지 않는다.
public record ChainDocumentReceipt(
        String contractAddress,
        String transactionHash,
        BigInteger blockNumber
) {
}
