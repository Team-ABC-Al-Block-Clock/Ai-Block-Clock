package com.aiblockclock.domain.node;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

// 테스트 실행은 엔진 호출을 사이에 두고 앞(접수)과 뒤(닫기 · 원가 다시 정하기)에 쓰기가 있다.
// 엔진 호출은 트랜잭션 안에 둘 수 없어, 실행 선차감(ExecutionReservationManager)과 같이 앞과 뒤를 각각 한 트랜잭션으로 둔다.
@Component
@RequiredArgsConstructor
public class NodeTestRunManager {
    private final NodeDefinitionValidator nodeDefinitionValidator;
    private final NodeDefinitionWriter nodeDefinitionWriter;
    private final NodeTestRunReader nodeTestRunReader;
    private final NodeTestRunWriter nodeTestRunWriter;
    private final BaseCostCalculator baseCostCalculator;
    private final NodeTestRunProperties properties;
    private final Clock clock;

    // 노드 행을 잠근 채 주인인지 보고 오늘(UTC) 횟수를 센다. 같은 노드의 테스트가 동시에 와도 줄을 서서 한도를 넘기지 않는다.
    @Transactional
    public NodeTestRun reserve(NodeDefinitionId nodeId, UserId requester, boolean mock) {
        nodeDefinitionValidator.ensureEditable(nodeId, requester);

        Instant now = clock.instant();

        if (nodeTestRunReader.countSince(nodeId, now.truncatedTo(ChronoUnit.DAYS)) >= properties.dailyLimit()) {
            throw new CoreException(ErrorType.NODE_TEST_RUN_LIMIT_EXCEEDED,
                    Map.of("dailyLimit", properties.dailyLimit()));
        }

        return nodeTestRunWriter.append(nodeId, requester, mock, now);
    }

    @Transactional
    public NodeTestRun dispatch(NodeTestRunId id, String runId) {
        return nodeTestRunWriter.dispatch(id, runId);
    }

    // 엔진에 넘기지 못한 테스트다. 돌지 않았으니 하루 횟수를 쓰지 않은 것으로 되돌린다.
    @Transactional
    public void discard(NodeTestRunId id) {
        nodeTestRunWriter.remove(id);
    }

    // 원가를 잰 테스트가 닫히면 노드 원가를 다시 정한다. 노드 행을 잠근 뒤에 최근 기록을 읽어,
    // 같은 노드의 테스트 둘이 동시에 끝나도 늦게 닫는 쪽이 먼저 닫힌 기록까지 보고 정한다.
    @Transactional
    public Optional<NodeTestRun> finish(NodeTestRunId id, NodeTestRunOutcome outcome) {
        Optional<NodeTestRun> closed = nodeTestRunWriter.finish(id, outcome, clock.instant());

        closed.filter(NodeTestRun::measuredCost)
                .ifPresent(run -> reprice(run.nodeId()));

        return closed;
    }

    private void reprice(NodeDefinitionId nodeId) {
        nodeDefinitionWriter.lock(nodeId);
        nodeDefinitionWriter.reprice(nodeId, baseCostCalculator.calculate(
                nodeTestRunReader.readRecentCosts(nodeId, baseCostCalculator.sampleSize())));
    }
}
