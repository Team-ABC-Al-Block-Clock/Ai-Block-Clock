package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.execution.NodeTestRunResult;
import com.aiblockclock.domain.node.NodeTestRun;
import com.aiblockclock.enums.node.NodeTestRunStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "노드 테스트 실행")
public record NodeTestRunResponse(
        @Schema(description = "테스트 실행 ID", example = "4b2e8c1a-6d3f-4a9b-8e7c-1f5d2a9b3c60")
        String testRunId,

        @Schema(description = "테스트한 노드 ID", example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13")
        String nodeId,

        @Schema(description = "RUNNING 이면 2초쯤 뒤에 다시 조회한다. SUCCEEDED · FAILED 면 끝이다",
                example = "SUCCEEDED")
        NodeTestRunStatus status,

        @Schema(description = "모델을 부르지 않은 테스트인가", example = "false")
        boolean mock,

        @Schema(description = "엔진이 잰 이번 테스트의 원가(달러). 끝나기 전이나 모델을 부르기 전에 실패하면 비어 있다",
                example = "0.007432", nullable = true)
        String costUsd,

        @Schema(description = """
                지금 노드의 1회 실행 원가(ABC). 원가를 잰 테스트가 끝나면 최근 테스트들의 중앙값 × 1.5 로 새로 정한 값이 온다""",
                example = "0.011148")
        String baseCost,

        @Schema(description = "통화", example = "ABC")
        String currency,

        @Schema(description = "실패한 까닭. 엔진의 오류 코드 그대로다", example = "MODEL_TIMEOUT", nullable = true)
        String failureCode,

        @Schema(description = "접수한 때")
        Instant requestedAt,

        @Schema(description = "끝난 때", nullable = true)
        Instant finishedAt,

        @Schema(description = """
                성공한 테스트의 결과물. 엔진이 씌운 봉투 JSON 이고 글 출력은 payload.text 가 본문이다. 닫을 때 한 번 받아 둔 것이다.
                끝나기 전, 실패했을 때, 엔진이 결과물을 주지 못했거나 JSON 이 아닌 것을 주었을 때는 비어 있다""",
                nullable = true, type = "object")
        JsonNode result
) {
    // 결과물은 닫을 때 JSON 으로 읽어 본 것만 남겨 두었다. 응답에는 문자열이 아니라 JSON 객체로 싣는다.
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public static NodeTestRunResponse from(NodeTestRunResult result) {
        NodeTestRun run = result.run();

        return new NodeTestRunResponse(
                run.id().value().toString(),
                run.nodeId().value().toString(),
                run.status(),
                run.mock(),
                run.costUsd() == null ? null : run.costUsd().toPlainString(),
                result.baseCost().value().toPlainString(),
                AbcPricing.CURRENCY,
                run.failureCode(),
                run.requestedAt(),
                run.finishedAt(),
                resultOf(run));
    }

    private static JsonNode resultOf(NodeTestRun run) {
        return run.resultJson() == null ? null : MAPPER.readTree(run.resultJson());
    }
}
