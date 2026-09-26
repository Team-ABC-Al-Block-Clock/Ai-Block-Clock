package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.payment.PaymentMySqlTest;

// 시드 스크립트는 운영 DB(MySQL)에서 손으로 넣는다. H2 가 받아 주는 문법이 MySQL 에서도 되는지, CHECK 제약이 MySQL 에서도 막는지 본다.
@PaymentMySqlTest
class AgentGradeMetricMySqlTest extends AgentGradeMetricIntegrationTest {
}
