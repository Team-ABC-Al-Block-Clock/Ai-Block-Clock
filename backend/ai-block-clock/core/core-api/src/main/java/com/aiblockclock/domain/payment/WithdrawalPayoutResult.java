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
        // 체인이 거래를 되돌려 실패로 닫고 묶은 금액을 정산금으로 돌려놓았다
        FAILED,
        // 보낸 지 오래됐는데 결과를 몰라 보류로 넘겼다. 금액은 묶여 있고 뒤의 출금도 기다린다
        HELD,
        // 보관 지갑 잔액이 모자라 보내지 않았다
        CUSTODY_BALANCE_SHORT,
        // 체인에 닿지 못했다. 다음 차례에 다시 본다
        UNREACHABLE;

        // 보낸 출금이 닫혀 보관 지갑이 다음 출금을 보낼 수 있다
        boolean closesPayout() {
            return this == PAID || this == FAILED;
        }
    }

    static WithdrawalPayoutResult idle() {
        return new WithdrawalPayoutResult(Outcome.IDLE, null);
    }

    static WithdrawalPayoutResult of(Outcome outcome, Withdrawal withdrawal) {
        return new WithdrawalPayoutResult(outcome, withdrawal.getId());
    }
}
