package com.aiblockclock.domain.payment;

// 동시 요청을 한 줄로 세우는 잠금과, 잠금 뒤에 읽어야 앞 요청이 보인다는 규칙은 MySQL(REPEATABLE READ)의 동작이다.
// H2 와 다를 수 있어 운영과 같은 MySQL 에서도 같은 결과를 내는지 본다.
@PaymentMySqlTest
class PayoutConversionServiceMySqlTest extends PayoutConversionServiceIntegrationTest {
}
