package com.aiblockclock.domain.execution;

// 실행이 닫혔다(환불 · 정산까지 끝났다). 닫는 트랜잭션이 커밋된 뒤에만 받는다 — 되돌려진 닫기의 근거를 적지 않게.
// report 는 닫을 때 엔진에게서 들은 것이다. 엔진 원본은 담지 않는다. 원본을 받는 일은 근거를 적는 쪽이 따로 한다.
public record ExecutionClosed(ExecutionId id, EngineReport report) {
}
