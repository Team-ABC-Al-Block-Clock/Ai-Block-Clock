package com.aiblockclock.client.ai;

import java.time.Instant;
import java.util.Map;

import tools.jackson.databind.JsonNode;

// 런타임 → abc.events / run.<type> 으로 오는 실행 이벤트를 읽은 것이다 (ai/docs/integration-guide.md §4.4).
// snapshot 은 GET /v1/runs/{id} 응답과 같은 모양이라 RunStatus 로 읽는다. snapshotJson 은 그 snapshot 의 원문이다 —
// 노드 출력처럼 RunStatus 가 읽지 않는 칸까지 남아 있어 정산 근거로 그대로 적는다.
// artifact · resultFiles 는 succeeded 에만 온다. 없으면 null 이다.
public record RunEvent(
        String executionId,
        RunMode mode,
        int attempt,
        int seq,
        RunEventType type,
        Instant occurredAt,
        RunStatus snapshot,
        String snapshotJson,
        JsonNode artifact,
        Map<String, String> resultFiles
) {
}
