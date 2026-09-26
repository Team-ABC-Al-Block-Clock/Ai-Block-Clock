package com.aiblockclock.client.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RunStatus(
        @JsonProperty("run_id") String runId,
        String status,
        Progress progress,
        List<Node> nodes,
        List<Contribution> contribution,
        @JsonProperty("not_executed") List<NotExecuted> notExecuted,
        Result result,
        Failure failure
) {
    public RunState state() {
        return RunState.from(status);
    }

    public boolean isFinished() {
        return state().isFinished();
    }

    public boolean isSucceeded() {
        return state() == RunState.SUCCEEDED;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Node(
            String id,
            String name,
            String type,
            String status,
            @JsonProperty("agent_id") String agentId,
            @JsonProperty("owner_id") String ownerId,
            @JsonProperty("cost_abc") BigDecimal costAbc,
            @JsonProperty("skip_reason") String skipReason
    ) {
        private static final String SUCCEEDED = "succeeded";

        public boolean isSucceeded() {
            return SUCCEEDED.equals(status);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contribution(
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("owner_id") String ownerId,
            @JsonProperty("agent_id") String agentId,
            @JsonProperty("charged_abc") BigDecimal chargedAbc
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotExecuted(
            @JsonProperty("node_id") String nodeId,
            String reason,
            @JsonProperty("refund_abc") BigDecimal refundAbc
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            String type,
            @JsonProperty("schema_id") String schemaId,
            @JsonProperty("artifact_url") String artifactUrl,
            @JsonProperty("pdf_url") String pdfUrl
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
