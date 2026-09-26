package com.aiblockclock.domain.node;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.node.NodeTestRunStatus;
import com.aiblockclock.storage.node.NodeTestRunEntity;
import com.aiblockclock.storage.node.NodeTestRunRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NodeTestRunWriter {
    private final NodeTestRunRepository nodeTestRunRepository;

    @Transactional
    public NodeTestRun append(NodeDefinitionId nodeId, UserId requester, boolean mock, Instant requestedAt) {
        return NodeTestRunEntityConverter.toDomain(nodeTestRunRepository.save(new NodeTestRunEntity(
                NodeTestRunId.newId().value(), nodeId.value(), requester.value(), mock, requestedAt)));
    }

    @Transactional
    public NodeTestRun dispatch(NodeTestRunId id, String runId) {
        NodeTestRunEntity entity = entity(id);
        entity.dispatch(runId);

        return NodeTestRunEntityConverter.toDomain(entity);
    }

    @Transactional
    public void remove(NodeTestRunId id) {
        nodeTestRunRepository.deleteById(id.value());
    }

    @Transactional
    public void removeAll(NodeDefinitionId nodeId) {
        nodeTestRunRepository.deleteAllByNodeId(nodeId.value());
    }

    // 행을 잠근 채 엔진이 받은 테스트일 때만 닫는다. 조회가 겹쳐 같은 끝을 두 번 봐도 한 번만 닫힌다.
    @Transactional
    public Optional<NodeTestRun> finish(NodeTestRunId id, NodeTestRunOutcome outcome, Instant finishedAt) {
        NodeTestRunEntity entity = nodeTestRunRepository.findByIdForUpdate(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.NODE_TEST_RUN_NOT_FOUND));

        if (entity.getStatus() != NodeTestRunStatus.RUNNING) {
            return Optional.empty();
        }

        entity.finish(outcome.status(), outcome.costUsd(), outcome.failureCode(), finishedAt);

        return Optional.of(NodeTestRunEntityConverter.toDomain(entity));
    }

    private NodeTestRunEntity entity(NodeTestRunId id) {
        return nodeTestRunRepository.findById(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.NODE_TEST_RUN_NOT_FOUND));
    }
}
