package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunAccepted;
import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.RunRequest;
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
import com.aiblockclock.domain.node.RefFileStore;
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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 판매자가 노드 하나를 엔진에 실제로 돌려 결과물을 보고, 엔진이 잰 달러 원가로 노드 원가를 정한다 (NOD-007, 엔진 계약 v2.0 mode=test).
// 사용자 돈은 움직이지 않는다. 엔진은 test 실행의 contribution 을 비워 보내 정산할 것도 없다.
// 끝은 판매자가 조회할 때 엔진에 물어 안다. 실행처럼 뒤에서 따라가지 않는 까닭은, 돈이 묶이지 않아 늦게 닫혀도 잃는 것이 없어서다.
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeTestRunService {
    private static final NodeKey TESTED_SLOT = NodeKey.of(RuntimeWorkflow.stepIdOf(0));
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final NodeTestRunManager nodeTestRunManager;
    private final NodeTestRunReader nodeTestRunReader;
    private final NodeDefinitionReader nodeDefinitionReader;
    private final RefFileReader refFileReader;
    private final RefFileStore refFileStore;
    private final AiRuntimeClient aiRuntimeClient;

    // 접수(주인 · 하루 횟수 확인)를 먼저 적고 엔진에 넘긴다.
    // 엔진이 받은 뒤에는 접수를 지우지 않는다. 엔진은 이미 돌고 있어, 지우면 그 실행으로 잰 원가도 결과물도 잃는다.
    public NodeTestRunResult start(NodeDefinitionId nodeId, UserId requester, String comment, boolean mock) {
        NodeDefinition node = nodeDefinitionReader.read(nodeId);
        NodeTestRun reserved = nodeTestRunManager.reserve(nodeId, requester, mock);
        RunAccepted accepted = send(reserved, node, comment, mock);

        return new NodeTestRunResult(nodeTestRunManager.dispatch(reserved.id(), accepted.runId()),
                node.getSpec().baseCost());
    }

    // 아직 도는 테스트면 엔진에 물어 끝났는지 본다. 엔진이 답하지 못하면 남긴 것으로 답하고 다음 조회에 다시 묻는다.
    // 끝난 테스트는 닫을 때 받아 둔 결과물로 답해, 다시 열어 볼 때마다 엔진을 부르지 않는다.
    public NodeTestRunResult follow(NodeDefinitionId nodeId, NodeTestRunId id, UserId requester) {
        NodeTestRun run = mine(nodeId, id, requester);

        if (run.isRunning()) {
            run = settle(run);
        }

        return new NodeTestRunResult(run, nodeDefinitionReader.read(nodeId).getSpec().baseCost());
    }

    // 엔진에 넘기지 못한 테스트는 돌지 않았으니 접수를 지워 하루 횟수를 쓰지 않게 한다.
    private RunAccepted send(NodeTestRun reserved, NodeDefinition node, String comment, boolean mock) {
        try {
            RuntimeWorkflow workflow = RuntimeWorkflow.forTest(reserved.id().value().toString(), promptOf(node));

            return aiRuntimeClient.start(new RunRequest(
                    workflow.json(), null, comment, null, refFilePathsOf(workflow), mock, RunMode.TEST));
        } catch (RuntimeException exception) {
            log.warn("테스트 실행을 맡기지 못해 접수를 지운다: testRun={}", reserved.id().value(), exception);
            nodeTestRunManager.discard(reserved.id());

            if (exception instanceof AiClientException) {
                throw new CoreException(ErrorType.EXECUTION_ENGINE_UNAVAILABLE);
            }

            throw exception;
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

    // 결과물은 엔진 호출이라 닫는 트랜잭션 밖에서 먼저 받는다. 조회가 겹쳐 둘 다 받아 와도 닫기는 한 번만 된다.
    private NodeTestRun settle(NodeTestRun run) {
        RunStatus status;

        try {
            status = aiRuntimeClient.read(run.runId());
        } catch (AiClientException exception) {
            log.warn("테스트 실행의 진행 상황을 가져오지 못했다: testRun={} {}", run.id().value(), exception.getMessage());

            return run;
        }

        if (!status.isFinished()) {
            return run;
        }

        String result = status.isSucceeded() ? resultOf(run) : null;

        return nodeTestRunManager.finish(run.id(), outcomeOf(status, result))
                .orElseGet(() -> nodeTestRunReader.read(run.id()));
    }

    // 엔진 본문을 그대로 믿지 않고 JSON 인지 읽어 본 뒤에만 남긴다. 앞단이 오류를 200 으로 주거나 본문이 비면
    // 결과물 없이 닫는다. 그 값이 응답에 그대로 실리면 판매자 화면이 응답 전체를 읽지 못한다.
    private String resultOf(NodeTestRun run) {
        String body;

        try {
            body = aiRuntimeClient.readArtifact(run.runId());
        } catch (AiClientException exception) {
            log.warn("테스트 실행의 결과물을 가져오지 못해 결과물 없이 닫는다: testRun={} {}",
                    run.id().value(), exception.getMessage());

            return null;
        }

        JsonNode parsed;

        try {
            parsed = body == null ? null : MAPPER.readTree(body);
        } catch (JacksonException exception) {
            parsed = null;
        }

        if (parsed == null || parsed.isMissingNode() || parsed.isNull()) {
            log.warn("테스트 실행의 결과물이 JSON 이 아니라 결과물 없이 닫는다: testRun={}", run.id().value());

            return null;
        }

        return MAPPER.writeValueAsString(parsed);
    }

    // 원가는 실행 전체의 합계로 읽는다. 합계가 없으면 단계마다 잰 값을 더한다.
    private static NodeTestRunOutcome outcomeOf(RunStatus status, String result) {
        NodeTestRunStatus closed = status.isSucceeded() ? NodeTestRunStatus.SUCCEEDED : NodeTestRunStatus.FAILED;
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

    private PlannedPrompt promptOf(NodeDefinition node) {
        return new PlannedPrompt(TESTED_SLOT, node.getSpec().name(), node.getOwner(), node.getPrompt(),
                node.getSpec().model(), InputSource.defaultSource(), refFileReader.read(node.getId()),
                node.getSpec().baseCost());
    }

    private Map<String, List<Path>> refFilePathsOf(RuntimeWorkflow workflow) {
        return workflow.refFiles().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream().map(refFileStore::locate).toList()));
    }
}
