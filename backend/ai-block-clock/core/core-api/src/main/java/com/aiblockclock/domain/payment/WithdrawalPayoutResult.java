package com.aiblockclock.domain.payment;

// 지급 차례 한 번에 무엇을 했는지다. 차례를 도는 쪽이 로그를 남기고 다음 간격을 정하는 데 쓴다.
public record WithdrawalPayoutResult(Outcome outcome, Long withdrawalId) {

    public enum Outcome {
        // 보낼 출금이 없다
        IDLE,
        // 새 출금을 서명해 보냈다
        SUBMITTED,
        // 보낸 거래가 아직 블록에 적히지 않았다. 사라졌으면 같은 거래를 다시 보냈다
        WAITING,
        // 체인에서 성공을 확인하고 끝냈다
        PAID,
        // 보관 지갑 잔액이 모자라 보내지 않았다
        CUSTODY_BALANCE_SHORT,
        // 체인이 거래를 되돌렸다. 거래 번호를 다시 맞춰야 해서 뒤의 출금도 멈춘다(복구는 PAY-026 · CHN-008)
        FAILED_ON_CHAIN,
        // 체인에 닿지 못했다. 다음 차례에 다시 본다
        UNREACHABLE
    }

    static WithdrawalPayoutResult idle() {
        return new WithdrawalPayoutResult(Outcome.IDLE, null);
    }

    static WithdrawalPayoutResult of(Outcome outcome, Withdrawal withdrawal) {
        return new WithdrawalPayoutResult(outcome, withdrawal.getId());
    }
}
