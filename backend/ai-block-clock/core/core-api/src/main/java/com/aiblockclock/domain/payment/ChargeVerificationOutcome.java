package com.aiblockclock.domain.payment;

public enum ChargeVerificationOutcome {
    WAITING,
    VERIFIED,
    REJECTED,
    // 체인에 묻지 못했다. WAITING 과 달리 대기 한도를 재지 않는다 — 거래가 이미 성공했을 수도 있다
    UNREACHABLE
}
