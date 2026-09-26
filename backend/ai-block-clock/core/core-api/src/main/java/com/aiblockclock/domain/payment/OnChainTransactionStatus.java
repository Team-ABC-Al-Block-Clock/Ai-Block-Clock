package com.aiblockclock.domain.payment;

public enum OnChainTransactionStatus {
    NOT_FOUND,
    PENDING,
    SUCCESS,
    FAILED,
    // 체인 노드에 닿지 못해 거래가 어떻게 됐는지 모른다. 실패로 단정하지 않고 다음 확인을 기다린다
    UNKNOWN
}
