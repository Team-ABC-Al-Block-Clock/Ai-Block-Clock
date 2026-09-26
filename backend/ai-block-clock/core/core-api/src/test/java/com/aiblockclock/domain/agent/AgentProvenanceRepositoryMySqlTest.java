package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.payment.PaymentMySqlTest;

// V36 마이그레이션이 MySQL 에서 돌고, 대기 목록 정렬(coalesce) · 유일 제약 · 조건부 UPDATE 가 MySQL 에서도 같은지 본다.
@PaymentMySqlTest
class AgentProvenanceRepositoryMySqlTest extends AgentProvenanceRepositoryIntegrationTest {
}
