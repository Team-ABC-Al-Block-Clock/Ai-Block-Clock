package com.aiblockclock.client.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

// 엔진은 돈을 모른다 (계약 v2.0 8.1). 토큰을 모델 공시 단가로 바꾼 달러 원가(cost_usd)까지만 주고,
// ABC 로 얼마를 받을지는 우리가 정한다. mock 은 모델을 부르지 않은 실행이라 원가가 모두 0 이다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record RunStatus(
        @JsonProperty("run_id") String runId,
        String status,
        Progress progress,
        List<Node> nodes,
        List<Contribution> contribution,
        @JsonProperty("not_executed") List<NotExecuted> notExecuted,
        Result result,
        Failure failure,
        Boolean mock,
        Totals totals,
        Error error
) {
    public RunStatus(String runId, String status, Progress progress, List<Node> nodes,
                     List<Contribution> contribution, List<NotExecuted> notExecuted, Result result,
                     Failure failure, Boolean mock, Totals totals) {
        this(runId, status, progress, nodes, contribution, notExecuted, result, failure, mock, totals, null);
    }

    public RunState state() {
        return RunState.from(status);
    }

    public boolean isFinished() {
        return state().isFinished();
    }

    public boolean isSucceeded() {
        return state() == RunState.SUCCEEDED;
    }

    public boolean isMock() {
        return Boolean.TRUE.equals(mock);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Progress(
            int done,
            int total,
            int percent,
            @JsonProperty("current_node_id") String currentNodeId,
            @JsonProperty("current_node_name") String currentNodeName
    ) {
    }

    // cost_usd 는 모델을 부른 만큼이다. 부르기 전에 실패했거나 건너뛴 단계는 비어 있다 — 쓴 것이 없는 게 아니라 잰 것이 없다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Node(
            String id,
            String name,
            String type,
            String status,
            @JsonProperty("agent_id") String agentId,
            @JsonProperty("owner_id") String ownerId,
            @JsonProperty("cost_usd") BigDecimal costUsd,
            @JsonProperty("skip_reason") String skipReason,
            String model,
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens,
            Integer retries,
            @JsonProperty("repair_count") Integer repairCount,
            @JsonProperty("started_at") String startedAt,
            @JsonProperty("finished_at") String finishedAt,
            @JsonProperty("duration_ms") Long durationMs,
            @JsonProperty("branch_taken") Boolean branchTaken
    ) {
        private static final String SUCCEEDED = "succeeded";
        private static final String SKIPPED = "skipped";

        public Node(String id, String name, String type, String status, String agentId, String ownerId,
                    BigDecimal costUsd, String skipReason) {
            this(id, name, type, status, agentId, ownerId, costUsd, skipReason,
                    null, null, null, null, null, null, null, null, null);
        }

        public boolean isSucceeded() {
            return SUCCEEDED.equals(status);
        }

        // 분기에서 고르지 않은 쪽이라 돌지 않은 단계다. 실패도 미완료도 아니다.
        public boolean isSkipped() {
            return SKIPPED.equals(status);
        }
    }

    // 실행 전체에서 쓴 달러 원가다. 판매자 테스트 실행의 원가를 잴 때 읽는다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Totals(
            @JsonProperty("cost_usd") BigDecimal costUsd
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contribution(
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("owner_id") String ownerId,
            @JsonProperty("agent_id") String agentId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotExecuted(
            @JsonProperty("node_id") String nodeId,
            String reason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            String type,
            @JsonProperty("artifact_url") String artifactUrl,
            @JsonProperty("pdf_url") String pdfUrl
    ) {
    }

    // 실패한 실행의 자세한 사유다. detail 은 엔진이 본 원인(예: 어긴 출력 모양)이고 화면용이 아니다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(
            String code,
            String message,
            String detail,
            @JsonProperty("node_id") String nodeId
    ) {
    }

    // fault 는 실패 책임이다. supplier · consumer · platform 중 하나가 온다 (계약 8.5).
    // affects_success_rate 는 공급자 성공률에 넣을지다. 공급자 탓이어도 넣지 않는 오류가 있다 (계약 9.2).
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Failure(
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("error_code") String errorCode,
            String fault,
            @JsonProperty("affects_success_rate") Boolean affectsSuccessRate
    ) {
    }
}
