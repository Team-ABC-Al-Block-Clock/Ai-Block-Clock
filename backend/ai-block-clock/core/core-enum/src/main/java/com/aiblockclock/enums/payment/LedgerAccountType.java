package com.aiblockclock.enums.payment;

// WITHDRAWAL_ESCROW 는 출금을 신청해 심사를 기다리는 정산금이다. 정산금에서 빼 두어 같은 돈으로 두 번 신청하지 못하고,
// 지급되면 체인으로 나가고 취소 · 반려되면 정산금으로 돌아간다. 실행 예치(EXECUTION_ESCROW)와 같은 역할을 출금에서 한다.
public enum LedgerAccountType {
    ABC_BALANCE,
    EXECUTION_ESCROW,
    PAYOUT_BALANCE,
    ABC_ISSUANCE,
    WITHDRAWAL_ESCROW
}
