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

// 판매자가 노드 하나를 엔진에 실제로 돌려 결과물을 보고, 엔진이 잰 달러 원가로 노드 원가를 정한다 (NOD-007, 엔진 계약 v2.0 mode=test).
// 사용자 돈은 움직이지 않는다. 엔진은 test 실행의 contribution 을 비워 보내 정산할 것도 없다.
// 끝은 판매자가 조회할 때 엔진에 물어 안다. 실행처럼 뒤에서 따라가지 않는 까닭은, 돈이 묶이지 않아 늦게 닫혀도 잃는 것이 없어서다.
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeTestRunService {
    private static final NodeKey TESTED_SLOT = NodeKey.of(RuntimeWorkflow.stepIdOf(0));

    private final NodeTestRunManager nodeTestRunManager;
    private final NodeTestRunReader nodeTestRunReader;
    private final NodeDefinitionReader nodeDefinitionReader;
    private final RefFileReader refFileReader;
    private final RefFileStore refFileStore;
    private final AiRuntimeClient aiRuntimeClient;

    // 접수(주인 · 하루 횟수 확인)를 먼저 적고 엔진에 넘긴다. 넘기지 못하면 접수를 지워 횟수를 쓰지 않게 한다.
    public NodeTestRunResult start(NodeDefinitionId nodeId, UserId requester, String comment, boolean mock) {
        NodeTestRun reserved = nodeTestRunManager.reserve(nodeId, requester, mock);

        try {
            NodeDefinition node = nodeDefinitionReader.read(nodeId);
            RuntimeWorkflow workflow = RuntimeWorkflow.forTest(reserved.id().value().toString(), promptOf(node));
            RunAccepted accepted = aiRuntimeClient.start(new RunRequest(
                    workflow.json(), null, comment, null, refFilePathsOf(workflow), mock, RunMode.TEST));

            return new NodeTestRunResult(nodeTestRunManager.dispatch(reserved.id(), accepted.runId()),
                    node.getSpec().baseCost(), null);
        } catch (RuntimeException exception) {
            log.warn("테스트 실행을 맡기지 못해 접수를 지운다: testRun={}", reserved.id().value(), exception);
            nodeTestRunManager.discard(reserved.id());

            if (exception instanceof AiClientException) {
                throw new CoreException(ErrorType.EXECUTION_ENGINE_UNAVAILABLE);
            }

            throw exception;
        }
    }

    // 아직 도는 테스트면 엔진에 물어 끝났는지 본다. 엔진이 답하지 못하면 남긴 것으로 답하고 다음 조회에 다시 묻는다.
    public NodeTestRunResult follow(NodeDefinitionId nodeId, NodeTestRunId id, UserId requester) {
        NodeTestRun run = mine(nodeId, id, requester);

        if (run.isRunning()) {
            run = settle(run);
        }

        return new NodeTestRunResult(run, nodeDefinitionReader.read(nodeId).getSpec().baseCost(), artifactOf(run));
    }

    // 남의 테스트와 다른 노드의 테스트는 있는지도 알리지 않는다.
    private NodeTestRun mine(NodeDefinitionId nodeId, NodeTestRunId id, UserId requester) {
        NodeTestRun run = nodeTestRunReader.read(id);

        if (!run.isOf(nodeId) || !run.isRequestedBy(requester)) {
            throw new CoreException(ErrorType.NODE_TEST_RUN_NOT_FOUND);
        }

        return run;
    }

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

        return nodeTestRunManager.finish(run.id(), outcomeOf(status))
                .orElseGet(() -> nodeTestRunReader.read(run.id()));
    }

    private String artifactOf(NodeTestRun run) {
        if (!run.isSucceeded()) {
            return null;
        }

        try {
            return aiRuntimeClient.readArtifact(run.runId());
        } catch (AiClientException exception) {
            log.info("엔진이 끝난 테스트 실행을 잊었다. 결과물 없이 답한다: testRun={}", run.id().value());

            return null;
        }
    }

    // 원가는 실행 전체의 합계로 읽는다. 합계가 없으면 단계마다 잰 값을 더한다.
    private static NodeTestRunOutcome outcomeOf(RunStatus status) {
        NodeTestRunStatus result = status.isSucceeded() ? NodeTestRunStatus.SUCCEEDED : NodeTestRunStatus.FAILED;
        String failureCode = status.failure() == null ? null : status.failure().errorCode();

        return new NodeTestRunOutcome(result, costOf(status), failureCode);
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
                InputSource.defaultSource(), refFileReader.read(node.getId()), node.getSpec().baseCost());
    }

    private Map<String, List<Path>> refFilePathsOf(RuntimeWorkflow workflow) {
        return workflow.refFiles().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream().map(refFileStore::locate).toList()));
    }
}
