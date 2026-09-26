package com.aiblockclock.api.v1;

import com.aiblockclock.domain.payment.PaymentMySqlTest;

// 정산 내역은 execution 과 엔티티 조인하고, 비어 있을 수 있는 에이전트 · 기간 조건을 IS NULL 로 건너뛴다.
// 이 쿼리와 시각 비교 · 정렬이 운영과 같은 MySQL 에서도 같은 결과를 내는지 본다.
@PaymentMySqlTest
class SettlementControllerMySqlTest extends SettlementControllerTest {
}
