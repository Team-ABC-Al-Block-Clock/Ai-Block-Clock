package com.aiblockclock.domain.payment;

// 출금 한 건을 보낼 거래를 준비한 결과다. 준비되면 서명한 거래가 있고, 아니면 왜 못 했는지만 있다.
public record PayoutPreparation(Outcome outcome, PayoutTransaction transaction) {

    public enum Outcome {
        PREPARED,
        // 보관 지갑의 abcToken 이 출금액보다 적다. 운영자가 채울 때까지 승인 상태로 기다린다
        CUSTODY_BALANCE_SHORT,
        // 체인 · 토큰증권 API 에 닿지 못했다. 다음 차례에 다시 준비한다
        UNREACHABLE
    }

    static PayoutPreparation prepared(PayoutTransaction transaction) {
        return new PayoutPreparation(Outcome.PREPARED, transaction);
    }

    static PayoutPreparation of(Outcome outcome) {
        return new PayoutPreparation(outcome, null);
    }
}
