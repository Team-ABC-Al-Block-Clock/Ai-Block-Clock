package com.aiblockclock.enums.payment;

// PLATFORM_EXPENSE 는 플랫폼이 대신 낸 ABC 다. 플랫폼 탓으로 실패한 실행은 사용자에게 전부 돌려주면서도
// 앞서 성공한 단계의 주인에게는 정산금을 준다 (엔진 계약 v2.0 8.5). 그 값을 이 계정이 내므로 잔액은 마이너스로 쌓인다.
// ABC_ISSUANCE 와 나눈 까닭은, 발행 계정은 체인 위 토큰으로 받친 ABC 만 세야 하기 때문이다.
public enum LedgerAccountType {
    ABC_BALANCE,
    EXECUTION_ESCROW,
    PAYOUT_BALANCE,
    ABC_ISSUANCE,
    PLATFORM_EXPENSE
}
