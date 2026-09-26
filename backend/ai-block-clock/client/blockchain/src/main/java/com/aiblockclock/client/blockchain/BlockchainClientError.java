package com.aiblockclock.client.blockchain;

// 호출하는 쪽이 "결과 불명(재조회)" 과 "실패 확인" 을 가르는 기준이다.
// REQUEST_FAILED 만 결과 불명이고, 나머지는 요청을 보내기 전이거나 응답을 받은 뒤의 실패다.
public enum BlockchainClientError {
    INVALID_ADDRESS,
    INVALID_TRANSACTION_HASH,
    INVALID_DOCUMENT,
    INVALID_TRANSACTION,
    INVALID_AMOUNT,
    INVALID_CONFIGURATION,
    MALFORMED_RESPONSE,
    TRANSACTION_REVERTED,
    REQUEST_FAILED
}
