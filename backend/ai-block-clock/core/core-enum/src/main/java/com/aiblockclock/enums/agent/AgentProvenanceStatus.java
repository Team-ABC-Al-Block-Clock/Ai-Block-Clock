package com.aiblockclock.enums.agent;

// 에이전트 버전의 원본 기록이 체인에 적혔는가다. 적히기 전에는 몇 번이고 다시 보내므로 실패 상태를 두지 않는다.
public enum AgentProvenanceStatus {
    PENDING,
    CONFIRMED
}
