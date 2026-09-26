package com.aiblockclock.domain.node;

import com.aiblockclock.enums.node.NodeTestRunStatus;
import com.aiblockclock.storage.node.NodeTestRunRepository;
import com.aiblockclock.storage.support.OffsetPageable;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NodeTestRunReader {
    private final NodeTestRunRepository nodeTestRunRepository;

    public NodeTestRun read(NodeTestRunId id) {
        return NodeTestRunEntityConverter.toDomain(nodeTestRunRepository.findById(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.NODE_TEST_RUN_NOT_FOUND)));
    }

    public long countSince(NodeDefinitionId nodeId, Instant since) {
        return nodeTestRunRepository.countByNodeIdAndRequestedAtGreaterThanEqual(nodeId.value(), since);
    }

    // 최근에 끝난 것부터 limit 개다. 원가를 정할 때 오래된 프롬프트로 잰 값이 계속 남지 않게 한다.
    public List<BigDecimal> readRecentCosts(NodeDefinitionId nodeId, int limit) {
        return nodeTestRunRepository.findRecentCosts(nodeId.value(), NodeTestRunStatus.SUCCEEDED,
                OffsetPageable.of(0, limit));
    }
}
