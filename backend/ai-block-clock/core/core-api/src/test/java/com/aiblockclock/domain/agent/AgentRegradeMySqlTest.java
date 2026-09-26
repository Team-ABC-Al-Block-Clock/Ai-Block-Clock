package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.payment.PaymentMySqlTest;

// 운영 DB(MySQL)에서도 V28 의 칸 · CHECK 제약 · 색인이 H2 와 같이 받고 막는지, 재산정이 같은 값을 남기는지 본다.
@PaymentMySqlTest
class AgentRegradeMySqlTest extends AgentRegradeIntegrationTest {
}
