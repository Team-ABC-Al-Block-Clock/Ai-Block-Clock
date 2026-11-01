package com.aiblockclock.api.batch;

import com.aiblockclock.domain.execution.Execution;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.domain.execution.ExecutionRequestId;
import com.aiblockclock.domain.execution.ExecutionService;
import com.aiblockclock.domain.execution.RunId;
import com.aiblockclock.domain.execution.UnfinishedExecution;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.enums.execution.ExecutionStatus;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

// 감시 목록은 메모리에 있어, 서버를 다시 띄우면 도는 실행을 아무도 감시하지 않는다. 복구가 DB 에서 다시 붙인다.
// 이벤트는 큐에 남아 있다가 다시 오므로 복구는 감시(기한)만 다시 건다. 넘기지 못한 실행은 디스패처의 몫이다.
// 넘긴 채 QUEUED 인 실행은 같은 주기에 큐 대기 안전망에 넘긴다. 한도가 지났는지는 안전망이 넘긴 시각으로 본다.
class ExecutionRecoverySchedulerTest {
    private static final Instant NOW = Instant.parse("2026-10-16T05:00:00Z");

    private final ExecutionService executionService = mock(ExecutionService.class);
    private final ExecutionRecoveryScheduler sut = new ExecutionRecoveryScheduler(executionService);

    @Test
    void 넘긴_채_RUNNING_인_실행은_감시에_다시_붙인다() {
        Execution running = execution(ExecutionStatus.RUNNING, true);
        given(executionService.readUnfinished()).willReturn(List.of(new UnfinishedExecution(running, NOW)));

        sut.recover();

        then(executionService).should().beginWatching(running);
    }

    @Test
    void 기동하자마자_한_번_다시_붙인다() {
        Execution running = execution(ExecutionStatus.RUNNING, true);
        given(executionService.readUnfinished()).willReturn(List.of(new UnfinishedExecution(running, NOW)));

        sut.recoverOnStartup();

        then(executionService).should().beginWatching(running);
    }

    // B6 · B14 — 아직 accepted 전이라 큐에서 기다리는 중이다. 대기는 TTL 과 dead letter 가 맡지만, dead letter 가 버려지면
    // 아무도 닫지 않는다. 그래서 같은 주기에 안전망에 넘긴다. 감시에는 붙이지 않는다.
    @Test
    void 넘긴_채_QUEUED_인_실행은_큐_대기_안전망에_넘긴다() {
        Execution queued = execution(ExecutionStatus.QUEUED, true);
        given(executionService.readUnfinished()).willReturn(List.of(new UnfinishedExecution(queued, NOW)));

        sut.recover();

        then(executionService).should().closeOverdueInQueue(queued.getId());
        then(executionService).should(never()).beginWatching(any());
    }

    // B8 — 런타임이 받은 실행은 감시가 맡는다.
    @Test
    void 넘긴_채_RUNNING_인_실행은_큐_대기_안전망에_넘기지_않는다() {
        Execution running = execution(ExecutionStatus.RUNNING, true);
        given(executionService.readUnfinished()).willReturn(List.of(new UnfinishedExecution(running, NOW)));

        sut.recover();

        then(executionService).should(never()).closeOverdueInQueue(any());
    }

    // B14 — 기동하자마자 한 번, 그 뒤 execution.recovery.interval 마다 돈다.
    @Test
    void 기동하자마자_넘긴_채_QUEUED_인_실행도_큐_대기_안전망에_넘긴다() {
        Execution queued = execution(ExecutionStatus.QUEUED, true);
        given(executionService.readUnfinished()).willReturn(List.of(new UnfinishedExecution(queued, NOW)));

        sut.recoverOnStartup();

        then(executionService).should().closeOverdueInQueue(queued.getId());
    }

    // 넘기지 못한 실행은 디스패처가 다시 발행하거나 한도가 지나면 버린다. 복구가 끼면 둘이 엉킨다.
    @Test
    void 넘기지_못한_실행은_오래됐어도_버리지도_발행하지도_않는다() {
        Execution stuck = execution(ExecutionStatus.QUEUED, false);
        given(executionService.readUnfinished())
                .willReturn(List.of(new UnfinishedExecution(stuck, NOW.minus(Duration.ofHours(1)))));

        sut.recover();

        then(executionService).should().readUnfinished();
        then(executionService).shouldHaveNoMoreInteractions();
    }

    // B12
    @Test
    void 큐_대기_안전망이_한_건을_닫다_실패해도_나머지는_본다() {
        Execution broken = execution(ExecutionStatus.QUEUED, true);
        Execution nextQueued = execution(ExecutionStatus.QUEUED, true);
        Execution nextRunning = execution(ExecutionStatus.RUNNING, true);
        given(executionService.readUnfinished()).willReturn(List.of(new UnfinishedExecution(broken, NOW),
                new UnfinishedExecution(nextQueued, NOW), new UnfinishedExecution(nextRunning, NOW)));
        willThrow(new IllegalStateException("boom")).given(executionService).closeOverdueInQueue(broken.getId());

        sut.recover();

        then(executionService).should().closeOverdueInQueue(nextQueued.getId());
        then(executionService).should().beginWatching(nextRunning);
    }

    // B12 — 실패한 건을 따로 기억하지 않아도 DB 에 QUEUED 로 남아 있어 다음 차례에 다시 읽힌다.
    @Test
    void 큐_대기_안전망이_닫다_실패한_건은_다음_차례에_다시_본다() {
        Execution broken = execution(ExecutionStatus.QUEUED, true);
        given(executionService.readUnfinished()).willReturn(List.of(new UnfinishedExecution(broken, NOW)));
        willThrow(new IllegalStateException("boom")).willDoNothing()
                .given(executionService).closeOverdueInQueue(broken.getId());

        sut.recover();
        sut.recover();

        then(executionService).should(times(2)).closeOverdueInQueue(broken.getId());
    }

    @Test
    void 한_건이_실패해도_나머지는_본다() {
        Execution broken = execution(ExecutionStatus.RUNNING, true);
        Execution next = execution(ExecutionStatus.RUNNING, true);
        given(executionService.readUnfinished()).willReturn(List.of(
                new UnfinishedExecution(broken, NOW), new UnfinishedExecution(next, NOW)));
        willThrow(new IllegalStateException("boom")).given(executionService).beginWatching(broken);

        sut.recover();

        then(executionService).should().beginWatching(next);
    }

    private static Execution execution(ExecutionStatus status, boolean dispatched) {
        ExecutionId id = ExecutionId.newId();

        return Execution.restore(id, ExecutionRequestId.of(UUID.randomUUID()), WorkflowId.of(UUID.randomUUID()),
                UserId.of(1L), dispatched ? RunId.of(id.value().toString()) : null, status, 2, null, false, null);
    }
}
