package com.aiblockclock.client.ai;

// 엔진이 준 실행 상태 원본이다. 노드 출력 전문까지 들었다(include_node_output=true). 프롬프트는 엔진이 내지 않는다.
// status 는 json 을 읽은 것이고, json 은 받은 바이트 그대로다 — 정산을 다툴 때 "엔진이 이렇게 말했다" 의 근거가 된다.
public record RunRecord(RunStatus status, String json) {
}
