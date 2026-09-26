package com.aiblockclock.domain.node;

import com.aiblockclock.domain.payment.PaymentMySqlTest;

// H2 와 MySQL 은 격리 수준과 잠금 구현이 달라, 운영과 같은 MySQL(REPEATABLE READ)에서도 한도와 원가가 같게 나오는지 본다.
@PaymentMySqlTest
class NodeTestRunMySqlTest extends NodeTestRunManagerIntegrationTest {
}
