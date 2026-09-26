package com.aiblockclock.domain.payment;

// 두 지급 차례가 겹칠 때 한 출금에 거래를 하나만 적는 조건부 UPDATE 와, 승인 시각 순서 읽기를 운영과 같은 MySQL 에서도 본다.
@PaymentMySqlTest
class WithdrawalPayoutServiceMySqlTest extends WithdrawalPayoutServiceIntegrationTest {
}
