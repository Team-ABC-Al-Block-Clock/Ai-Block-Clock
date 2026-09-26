package com.aiblockclock.enums.payment;

// PLATFORM_EXPENSE 는 플랫폼이 대신 낸 ABC 다. 플랫폼 탓으로 실패한 실행은 사용자에게 전부 돌려주면서도
// 앞서 성공한 단계의 주인에게는 정산금을 준다 (엔진 계약 v2.0 8.5). 그 값을 이 계정이 내므로 잔액은 마이너스로 쌓인다.
// ABC_ISSUANCE 와 나눈 까닭은, 발행 계정은 체인 위 토큰으로 받친 ABC 만 세야 하기 때문이다.
// WITHDRAWAL_ESCROW 는 출금을 신청해 심사를 기다리는 정산금이다. 정산금에서 빼 두어 같은 돈으로 두 번 신청하지 못하고,
// 지급되면 체인으로 나가고 취소 · 반려되면 정산금으로 돌아간다. 실행 예치(EXECUTION_ESCROW)와 같은 역할을 출금에서 한다.
public enum LedgerAccountType {
    ABC_BALANCE,
    EXECUTION_ESCROW,
    PAYOUT_BALANCE,
    ABC_ISSUANCE,
    PLATFORM_EXPENSE,
    WITHDRAWAL_ESCROW
}
