package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.payment.PaymentMySqlTest;

// 운영 DB(MySQL)에서도 agent_grade_history 의 칸 · CHECK 제약이 H2 와 같이 받고 막는지, 시드에서 이력까지 같은 값이 나오는지 본다.
@PaymentMySqlTest
class AgentGradeHistoryMySqlTest extends AgentGradeHistoryIntegrationTest {
}
