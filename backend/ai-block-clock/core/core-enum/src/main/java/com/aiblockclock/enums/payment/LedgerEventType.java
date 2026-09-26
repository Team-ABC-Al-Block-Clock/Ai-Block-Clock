package com.aiblockclock.enums.payment;

public enum LedgerEventType {
    CHARGE,
    RESERVE,
    REFUND,
    SETTLEMENT,
    WITHDRAWAL_HOLD,
    WITHDRAWAL_RELEASE,
    // 정산금을 같은 사람의 쓸 수 있는 잔액으로 옮긴다. 체인을 거치지 않는다
    PAYOUT_CONVERSION,
    // 체인으로 지급을 마친 출금 예치를 장부 밖으로 내보낸다. 충전(CHARGE)의 반대 방향이다
    WITHDRAWAL_PAYOUT
}
