package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.payment.PaymentMySqlTest;

// H2 와 MySQL 은 격리 수준과 잠금 구현이 달라, 운영과 같은 MySQL(REPEATABLE READ)에서도 같은 결과가 나오는지 본다.
@PaymentMySqlTest
class ExecutionReservationMySqlTest extends ExecutionReservationManagerIntegrationTest {
}
