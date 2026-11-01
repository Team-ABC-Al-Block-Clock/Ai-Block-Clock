package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunEvent;
import com.aiblockclock.client.ai.RunEventType;
import com.aiblockclock.client.ai.RunQueuePublisher;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeDefinitionReader;
import com.aiblockclock.domain.node.NodeTestRun;
import com.aiblockclock.domain.node.NodeTestRunId;
import com.aiblockclock.domain.node.NodeTestRunManager;
import com.aiblockclock.domain.node.NodeTestRunOutcome;
import com.aiblockclock.domain.node.NodeTestRunReader;
import com.aiblockclock.domain.node.RefFileReader;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.enums.node.NodeTestRunStatus;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 판매자가 노드 하나를 엔진에 실제로 돌려 결과물을 보고, 엔진이 잰 달러 원가로 노드 원가를 정한다 (NOD-007, 엔진 계약 v2.0 mode=test).
// 사용자 돈은 움직이지 않는다. 엔진은 test 실행의 contribution 을 비워 보내 정산할 것도 없다.
// 실행처럼 브로커로 맡기고 이벤트로 닫는다(ADR-0001). 다만 판매자가 응답을 기다리므로 발행은 confirm 까지 기다린다.
// 이벤트가 끊긴 테스트는 판매자가 조회할 때 기한을 넘겼으면 엔진에 한 번 물어 닫는다. 돈이 묶이지 않아 뒤에서 감시하지 않는다.
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeTestRunService {
    private static final String TESTED_STEP = "n1";
    private static final NodeKey TESTED_SLOT = NodeKey.of(TESTED_STEP);
    private static final int TESTED_STEP_COUNT = 1;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final NodeTestRunManager nodeTestRunManager;
    private final NodeTestRunReader nodeTestRunReader;
    private final NodeDefinitionReader nodeDefinitionReader;
    private final RefFileReader refFileReader;
    private final RunQueuePublisher runQueuePublisher;
    private final AiRuntimeClient aiRuntimeClient;
    private final ExecutionWatchProperties properties;
    private final Clock clock;

    // 접수(주인 · 하루 횟수 확인)를 먼저 적고 브로커에 넘긴다. confirm 을 받으면 테스트 실행 번호를 runId 로 남긴다.
    // 런타임이 받은 뒤에는 접수를 지우지 않는다. 런타임은 이미 돌고 있어, 지우면 그 실행으로 잰 원가도 결과물도 잃는다.
    public NodeTestRunResult start(NodeDefinitionId nodeId, UserId requester, String comment, boolean mock) {
        NodeDefinition node = nodeDefinitionReader.read(nodeId);
        NodeTestRun reserved = nodeTestRunManager.reserve(nodeId, requester, mock);

        submit(reserved, node, comment, mock);

        return new NodeTestRunResult(nodeTestRunManager.dispatch(reserved.id(), reserved.id().value().toString()),
                node.getSpec().baseCost());
    }

    // 끝난 테스트와 기한 안에 도는 테스트는 남긴 것으로 답한다. 기한을 넘긴 테스트만 엔진에 물어 끝났으면 닫는다.
    public NodeTestRunResult follow(NodeDefinitionId nodeId, NodeTestRunId id, UserId requester) {
        NodeTestRun run = mine(nodeId, id, requester);

        if (run.isRunning() && isOverdue(run)) {
            run = settle(run);
        }

        return new NodeTestRunResult(run, nodeDefinitionReader.read(nodeId).getSpec().baseCost());
    }

    // abc.events.backend 로 온 노드 테스트(mode=test)의 이벤트다. 끝을 알리는 이벤트만 테스트를 닫는다.
    // 이미 닫힌 테스트면 닫기가 행을 잠근 뒤 보고 아무것도 하지 않는다.
    // 모르는 테스트(번호가 UUID 가 아니거나 DB 에 없음)는 다시 와도 알게 되지 않아 경고만 남기고 넘어간다. DB 오류는 던진다.
    public void applyEvent(RunEvent event) {
        if (event.type() == RunEventType.ACCEPTED || event.type() == RunEventType.PROGRESS) {
            return;
        }

        Optional<NodeTestRunId> known = idOf(event.executionId());

        if (known.isEmpty()) {
            return;
        }

        NodeTestRunStatus status = event.type() == RunEventType.SUCCEEDED
                ? NodeTestRunStatus.SUCCEEDED
                : NodeTestRunStatus.FAILED;
        String result = status == NodeTestRunStatus.SUCCEEDED ? resultOf(event.artifact()) : null;

        try {
            nodeTestRunManager.finish(known.get(), outcomeOf(status, event.snapshot(), result));
        } catch (CoreException exception) {
            if (exception.getErrorType() != ErrorType.NODE_TEST_RUN_NOT_FOUND) {
                throw exception;
            }

            log.warn("모르는 테스트 실행의 메시지라 넘어간다: testRun={}", known.get().value());
        }
    }

    // abc.runs.dead 로 온 노드 테스트(mode=test)다. 런타임이 끝내지 못했으니 사유의 코드로 실패로 닫는다.
    public void closeDeadLettered(NodeTestRunId id, DeadLetterReason reason) {
        nodeTestRunManager.finish(id, new NodeTestRunOutcome(NodeTestRunStatus.FAILED, null, reason.failureCode(), null));
    }

    // 넘기지 못한 테스트는 접수를 지워 하루 횟수를 쓰지 않게 한다.
    // 발행이 실패했어도(nack · confirm 기한 초과 · 연결 실패) 브로커는 받았을 수 있다. 런타임이 늦게 받아 돌리면 플랫폼이
    // 모델 비용을 내므로, 그 테스트 번호로 취소를 먼저 건다 — 런타임은 취소를 기억해 두었다가 그 SubmitRun 을 돌리지 않는다.
    private void submit(NodeTestRun reserved, NodeDefinition node, String comment, boolean mock) {
        try {
            RuntimeWorkflow workflow = RuntimeWorkflow.forTest(reserved.id().value().toString(), promptOf(node));

            runQueuePublisher.submit(RunSubmission.forTest(reserved.id(), workflow, comment, mock, clock.instant()));
        } catch (AiClientException exception) {
            log.warn("테스트 실행을 맡기지 못해 취소를 걸고 접수를 지운다: testRun={}", reserved.id().value(), exception);
            cancelQuietly(reserved.id());
            nodeTestRunManager.discard(reserved.id());

            throw new CoreException(ErrorType.EXECUTION_ENGINE_UNAVAILABLE);
        } catch (RuntimeException exception) {
            log.warn("테스트 실행을 맡기지 못해 접수를 지운다: testRun={}", reserved.id().value(), exception);
            nodeTestRunManager.discard(reserved.id());

            throw exception;
        }
    }

    // 취소도 브로커에 닿지 못하면 더 할 수 있는 것이 없다. 판매자에게는 발행 실패와 같은 오류로 알린다.
    private void cancelQuietly(NodeTestRunId id) {
        try {
            runQueuePublisher.cancel(id.value().toString());
        } catch (RuntimeException exception) {
            log.info("맡기지 못한 테스트 실행에 취소를 걸지 못했다(브로커에 닿지 않음): testRun={} {}",
                    id.value(), exception.getMessage());
        }
    }

    // 테스트 실행 번호는 UUID 다. UUID 가 아니면 모르는 테스트라 비어 있다.
    private static Optional<NodeTestRunId> idOf(String testRunId) {
        try {
            return Optional.of(NodeTestRunId.of(UUID.fromString(testRunId)));
        } catch (IllegalArgumentException malformed) {
            log.warn("테스트 실행 번호가 UUID 가 아니라 모르는 테스트의 메시지로 보고 넘어간다: testRun={}", testRunId);

            return Optional.empty();
        }
    }

    // 남의 테스트와 다른 노드의 테스트는 있는지도 알리지 않는다.
    private NodeTestRun mine(NodeDefinitionId nodeId, NodeTestRunId id, UserId requester) {
        NodeTestRun run = nodeTestRunReader.read(id);

        if (!run.isOf(nodeId) || !run.isRequestedBy(requester)) {
            throw new CoreException(ErrorType.NODE_TEST_RUN_NOT_FOUND);
        }

        return run;
    }

    // 접수한 시각에서 기한(한 단계 × per-step + grace)이 꼭 된 시각이면 넘긴 것으로 본다.
    private boolean isOverdue(NodeTestRun run) {
        return !clock.instant().isBefore(run.requestedAt().plus(properties.deadline(TESTED_STEP_COUNT)));
    }

    // 결과물은 엔진 호출이라 닫는 트랜잭션 밖에서 먼저 받는다. 조회가 겹쳐 둘 다 받아 와도 닫기는 한 번만 된다.
    private NodeTestRun settle(NodeTestRun run) {
        RunStatus status;

        try {
            status = aiRuntimeClient.read(run.runId());
        } catch (AiClientException exception) {
            // 기한을 넘기도록 엔진이 이 테스트를 모른다. 다시 나타나지 않으니 실패로 닫는다 — 닫지 않으면 영영 도는 중으로 남는다.
            if (exception.getError() == AiClientError.RUN_NOT_FOUND) {
                log.warn("엔진이 테스트 실행을 몰라 실패로 닫는다: testRun={}", run.id().value());

                return nodeTestRunManager.finish(run.id(),
                                new NodeTestRunOutcome(NodeTestRunStatus.FAILED, null, ExecutionProgress.LOST, null))
                        .orElseGet(() -> nodeTestRunReader.read(run.id()));
            }

            log.warn("테스트 실행의 진행 상황을 가져오지 못했다: testRun={} {}", run.id().value(), exception.getMessage());

            return run;
        }

        if (!status.isFinished()) {
            return run;
        }

        NodeTestRunStatus closed = status.isSucceeded() ? NodeTestRunStatus.SUCCEEDED : NodeTestRunStatus.FAILED;
        String result = status.isSucceeded() ? artifactOf(run) : null;

        return nodeTestRunManager.finish(run.id(), outcomeOf(closed, status, result))
                .orElseGet(() -> nodeTestRunReader.read(run.id()));
    }

    // 엔진 본문을 그대로 믿지 않고 JSON 인지 읽어 본 뒤에만 남긴다. 앞단이 오류를 200 으로 주거나 본문이 비면
    // 결과물 없이 닫는다. 그 값이 응답에 그대로 실리면 판매자 화면이 응답 전체를 읽지 못한다.
    private String artifactOf(NodeTestRun run) {
        String body;

        try {
            body = aiRuntimeClient.readArtifact(run.runId());
        } catch (AiClientException exception) {
            log.warn("테스트 실행의 결과물을 가져오지 못해 결과물 없이 닫는다: testRun={} {}",
                    run.id().value(), exception.getMessage());

            return null;
        }

        try {
            return resultOf(body == null ? null : MAPPER.readTree(body));
        } catch (JacksonException exception) {
            log.warn("테스트 실행의 결과물이 JSON 이 아니라 결과물 없이 닫는다: testRun={}", run.id().value());

            return null;
        }
    }

    // 결과물을 JSON 으로 다시 쓴다. 비어 있으면 결과물 없이 닫는다.
    private static String resultOf(JsonNode artifact) {
        if (artifact == null || artifact.isMissingNode() || artifact.isNull()) {
            return null;
        }

        return MAPPER.writeValueAsString(artifact);
    }

    // 원가는 실행 전체의 합계로 읽는다. 합계가 없으면 단계마다 잰 값을 더한다. 실패 코드는 snapshot 의 failure 다.
    private static NodeTestRunOutcome outcomeOf(NodeTestRunStatus closed, RunStatus status, String result) {
        String failureCode = status.failure() == null ? null : status.failure().errorCode();

        return new NodeTestRunOutcome(closed, costOf(status), failureCode, result);
    }

    private static BigDecimal costOf(RunStatus status) {
        if (status.totals() != null && status.totals().costUsd() != null) {
            return status.totals().costUsd();
        }

        if (status.nodes() == null) {
            return null;
        }

        return status.nodes().stream()
                .map(RunStatus.Node::costUsd)
                .filter(Objects::nonNull)
                .reduce(BigDecimal::add)
                .orElse(null);
    }

    // 저장한 출력 형식 그대로 돌린다. 글로 재면 엔진의 형식 검사 · 교정이 없어 토큰이 덜 들고, 그만큼 원가가 낮게 잡힌다.
    private PlannedPrompt promptOf(NodeDefinition node) {
        return new PlannedPrompt(TESTED_STEP, TESTED_SLOT, node.getSpec().name(), node.getOwner(), node.getPrompt(),
                node.getSpec().model(), node.getSpec().output(), InputSource.defaultSource(),
                refFileReader.read(node.getId()), node.getSpec().baseCost());
    }
}
