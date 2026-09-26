package com.aiblockclock.domain.agent;

// 원본 기록이 체인에 적혀 성공한 것을 확인한 거래다.
public record AgentProvenanceReceipt(
        String contractAddress,
        String transactionHash,
        long blockNumber
) {
}
