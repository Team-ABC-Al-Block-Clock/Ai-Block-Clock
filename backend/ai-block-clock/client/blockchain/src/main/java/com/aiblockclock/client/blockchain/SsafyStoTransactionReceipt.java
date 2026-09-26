package com.aiblockclock.client.blockchain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigInteger;

// 토큰증권 API 가 쓰기 요청에 돌려주는 거래 영수증(TransactionReceiptResponse)에서 쓰는 칸만 읽는다.
@JsonIgnoreProperties(ignoreUnknown = true)
record SsafyStoTransactionReceipt(
        Boolean status,
        String transactionHash,
        BigInteger blockNumber
) {
}
