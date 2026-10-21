package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunEvent;
import com.aiblockclock.client.ai.RunEventType;
import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.RunStatus;

import java.time.Instant;

import tools.jackson.databind.json.JsonMapper;

// 런타임이 보내는 RunEvent 다 (contract 4장). 단계 둘(n1 성적 분석 · n2 가정통신문 작성)인 워크플로우를 돈다.
// snapshot 은 원문(JSON)을 먼저 쓰고 RunStatus 로 읽어, 원문과 읽은 것이 늘 같은 내용이게 한다.
// owner 는 런타임이 단계마다 돌려주는 주인(우리가 보낸 사용자 번호)이다. 정산이 이 값으로 공급자를 정한다.
public final class RunEventFixtures {
    public static final Instant OCCURRED_AT = Instant.parse("2026-10-16T05:00:03Z");
    public static final String ARTIFACT = """
            {"a2a_version":"2.0","node_id":"n2","schema_id":"text","payload":{"text":"이번 학기 평균이 올랐습니다"}}""";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private RunEventFixtures() {
    }

    public static RunEvent accepted(String executionId, int attempt, int seq) {
        return of(executionId, RunMode.RUN, attempt, seq, RunEventType.ACCEPTED, queuedSnapshot(executionId), null);
    }

    public static RunEvent progress(String executionId, int attempt, int seq, String owner) {
        return of(executionId, RunMode.RUN, attempt, seq, RunEventType.PROGRESS,
                firstStepDoneSnapshot(executionId, owner), null);
    }

    public static RunEvent succeeded(String executionId, int attempt, int seq, String owner) {
        return of(executionId, RunMode.RUN, attempt, seq, RunEventType.SUCCEEDED,
                succeededSnapshot(executionId, owner), ARTIFACT);
    }

    // n1 은 성공하고 n2 가 공급자 탓(MODEL_TIMEOUT)으로 실패했다.
    public static RunEvent failed(String executionId, int attempt, int seq, String owner) {
        return of(executionId, RunMode.RUN, attempt, seq, RunEventType.FAILED,
                secondStepFailedSnapshot(executionId, owner), null);
    }

    // 실행 전에 정의 오류로 거절했다. accepted 없이 온다.
    public static RunEvent rejected(String executionId, int attempt, int seq) {
        return of(executionId, RunMode.RUN, attempt, seq, RunEventType.REJECTED, rejectedSnapshot(executionId), null);
    }

    public static RunEvent rejectedWithoutFailure(String executionId, int attempt, int seq) {
        return of(executionId, RunMode.RUN, attempt, seq, RunEventType.REJECTED, """
                {"run_id":"%s","status":"failed","nodes":[]}""".formatted(executionId), null);
    }

    public static RunEvent of(String executionId, RunMode mode, int attempt, int seq, RunEventType type,
                              String snapshotJson, String artifactJson) {
        RunStatus snapshot = MAPPER.readValue(snapshotJson, RunStatus.class);

        return new RunEvent(executionId, mode, attempt, seq, type, OCCURRED_AT, snapshot, snapshotJson,
                artifactJson == null ? null : MAPPER.readTree(artifactJson), null);
    }

    public static String queuedSnapshot(String runId) {
        return """
                {"run_id":"%s","status":"queued","nodes":[]}""".formatted(runId);
    }

    public static String firstStepDoneSnapshot(String runId, String owner) {
        return """
                {"run_id":"%s","status":"running",
                 "progress":{"done":1,"total":2,"percent":50,"current_node_id":"n2","current_node_name":"가정통신문 작성"},
                 "nodes":[{"id":"n1","name":"성적 분석","type":"llm","status":"succeeded","owner_id":"%s",
                           "cost_usd":0.004,"output_payload":"{\\"text\\":\\"요약\\"}"},
                          {"id":"n2","name":"가정통신문 작성","type":"llm","status":"running","owner_id":"%s"}]}"""
                .formatted(runId, owner, owner);
    }

    public static String succeededSnapshot(String runId, String owner) {
        return """
                {"run_id":"%s","status":"succeeded",
                 "progress":{"done":2,"total":2,"percent":100},
                 "nodes":[{"id":"n1","name":"성적 분석","type":"llm","status":"succeeded","owner_id":"%s",
                           "cost_usd":0.004,"output_payload":"{\\"text\\":\\"요약\\"}"},
                          {"id":"n2","name":"가정통신문 작성","type":"llm","status":"succeeded","owner_id":"%s",
                           "cost_usd":0.002,"output_payload":"{\\"text\\":\\"통신문\\"}"}],
                 "totals":{"cost_usd":0.006}}""".formatted(runId, owner, owner);
    }

    public static String secondStepFailedSnapshot(String runId, String owner) {
        return """
                {"run_id":"%s","status":"failed",
                 "progress":{"done":1,"total":2,"percent":50,"current_node_id":"n2","current_node_name":"가정통신문 작성"},
                 "nodes":[{"id":"n1","name":"성적 분석","type":"llm","status":"succeeded","owner_id":"%s",
                           "cost_usd":0.004},
                          {"id":"n2","name":"가정통신문 작성","type":"llm","status":"failed","owner_id":"%s",
                           "cost_usd":0.001}],
                 "failure":{"node_id":"n2","error_code":"MODEL_TIMEOUT","fault":"supplier","affects_success_rate":true},
                 "totals":{"cost_usd":0.005}}""".formatted(runId, owner, owner);
    }

    public static String rejectedSnapshot(String runId) {
        return """
                {"run_id":"%s","status":"failed","nodes":[],
                 "error":{"code":"INVALID_WORKFLOW","message":"정의를 읽지 못했다"},
                 "failure":{"node_id":null,"error_code":"INVALID_WORKFLOW","fault":"consumer",
                            "affects_success_rate":false}}""".formatted(runId);
    }
}
