package com.aiblockclock.enums.payment;

// 충전 · 출금 내역 한 줄의 종류다. 둘 다 사용자 지갑과 보관 지갑 사이에서 abcToken 이 오가는 일이다(PAY-026).
public enum CreditHistoryType {
    // 사용자 지갑 → 보관 지갑. 확인되면 쓸 수 있는 ABC 가 된다
    CHARGE,
    // 보관 지갑 → 사용자 지갑. 정산금에서 나간다
    WITHDRAWAL
}
