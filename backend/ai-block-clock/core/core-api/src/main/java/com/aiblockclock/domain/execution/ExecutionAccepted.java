package com.aiblockclock.domain.execution;

// 실행을 접수했다(실행 · 선차감 · outbox 가 한 트랜잭션에 적혔다). 접수 트랜잭션이 커밋된 뒤에만 받는다 —
// 되돌려진 접수를 발행하지 않게. 받은 쪽(ExecutionDispatcher)은 요청 스레드를 붙잡지 않고 발행을 시도한다.
public record ExecutionAccepted(ExecutionId id) {
}
