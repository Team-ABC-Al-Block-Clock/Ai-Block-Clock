package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.RunQueuePublisher;
import com.aiblockclock.client.ai.SubmitRun;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.enums.execution.ExecutionStatus;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;

// 접수는 DB 에만 쓰고, 브로커로 넘기는 일은 디스패처가 한다(ADR-0001 핵심 규칙 1).
// confirm 을 받은 뒤에만 넘긴 기록(runId = 실행 번호)을 남긴다. 확인 직전에 죽어 두 번 나가도 런타임이 실행 번호로 거른다.
class ExecutionDispatcherTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant NOW = Instant.parse("2026-10-16T05:00:00Z");
    private static final Duration ABANDON_AFTER = Duration.ofMinutes(10);

    private final ExecutionReader executionReader = mock(ExecutionReader.class);
    private final ExecutionReservationManager reservationManager = mock(ExecutionReservationManager.class);
    private final RunQueuePublisher runQueuePublisher = mock(RunQueuePublisher.class);

    private final ExecutionDispatcher sut = new ExecutionDispatcher(executionReader, reservationManager,
            runQueuePublisher, new ExecutionDispatchProperties(Duration.ofSeconds(5), ABANDON_AFTER),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 넘기지_않은_실행을_발행하고_confirm_을_받으면_넘긴_기록을_남긴다() {
        PendingDispatch pending = pending(NOW.minusSeconds(1));
        ExecutionId id = pending.execution().getId();
        given(executionReader.findPendingDispatch(id)).willReturn(Optional.of(pending));
        given(reservationManager.markDispatched(id, NOW)).willReturn(true);

        assertThat(sut.dispatch(id)).isTrue();

        InOrder order = inOrder(runQueuePublisher, reservationManager);
        order.verify(runQueuePublisher).submit(pending.submit());
        order.verify(reservationManager).markDispatched(id, NOW);
    }

    @Test
    void confirm_을_받지_못하면_넘긴_기록을_남기지_않고_던지지도_않는다() {
        PendingDispatch pending = pending(NOW.minusSeconds(1));
        ExecutionId id = pending.execution().getId();
        given(executionReader.findPendingDispatch(id)).willReturn(Optional.of(pending));
        willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "nack")).given(runQueuePublisher).submit(any());

        assertThatCode(() -> assertThat(sut.dispatch(id)).isFalse()).doesNotThrowAnyException();

        then(reservationManager).should(never()).markDispatched(any(), any());
    }

    @Test
    void 발행하다_생각지_못한_오류가_나도_던지지_않는다() {
        PendingDispatch pending = pending(NOW.minusSeconds(1));
        ExecutionId id = pending.execution().getId();
        given(executionReader.findPendingDispatch(id)).willReturn(Optional.of(pending));
        willThrow(new IllegalStateException("boom")).given(runQueuePublisher).submit(any());

        assertThatCode(() -> sut.dispatch(id)).doesNotThrowAnyException();

        then(reservationManager).should(never()).markDispatched(any(), any());
    }

    @Test
    void 이미_넘겼거나_끝난_실행은_발행하지_않는다() {
        ExecutionId id = ExecutionId.newId();
        given(executionReader.findPendingDispatch(id)).willReturn(Optional.empty());

        assertThat(sut.dispatch(id)).isFalse();

        then(runQueuePublisher).shouldHaveNoInteractions();
    }

    // 커밋 직후 시도와 주기 점검이 겹치면 confirm 이 두 번 온다. 늦은 쪽은 기록할 것이 없을 뿐 오류가 아니다.
    @Test
    void 넘긴_기록이_이미_있으면_오류_없이_넘어간다() {
        PendingDispatch pending = pending(NOW.minusSeconds(1));
        ExecutionId id = pending.execution().getId();
        given(executionReader.findPendingDispatch(id)).willReturn(Optional.of(pending));
        given(reservationManager.markDispatched(id, NOW)).willReturn(false);

        assertThatCode(() -> assertThat(sut.dispatch(id)).isFalse()).doesNotThrowAnyException();
    }

    @Test
    void 주기_점검은_넘기지_않은_실행을_모두_발행한다() {
        PendingDispatch first = known(pending(NOW.minusSeconds(30)));
        PendingDispatch second = known(pending(NOW.minusSeconds(5)));
        given(executionReader.readPendingDispatches()).willReturn(List.of(first, second));

        sut.dispatchPending();

        then(runQueuePublisher).should().submit(first.submit());
        then(runQueuePublisher).should().submit(second.submit());
        then(reservationManager).should().markDispatched(first.execution().getId(), NOW);
        then(reservationManager).should().markDispatched(second.execution().getId(), NOW);
    }

    @Test
    void 주기_점검에서_한_건이_실패해도_나머지는_발행한다() {
        PendingDispatch broken = known(pending(NOW.minusSeconds(30)));
        PendingDispatch next = known(pending(NOW.minusSeconds(5)));
        given(executionReader.readPendingDispatches()).willReturn(List.of(broken, next));
        willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "nack"))
                .given(runQueuePublisher).submit(broken.submit());

        sut.dispatchPending();

        then(reservationManager).should(never()).markDispatched(broken.execution().getId(), NOW);
        then(reservationManager).should().markDispatched(next.execution().getId(), NOW);
    }

    // 넘기지 못한 채 오래된 실행이다. 혹시 넘어갔을지 몰라 취소를 걸고, 떼어 둔 금액을 돌려주며 플랫폼 탓으로 닫는다.
    @Test
    void 접수하고_한도가_지나도록_넘기지_못한_실행은_발행하지_않고_취소를_건_뒤_돌려준다() {
        PendingDispatch stale = known(pending(NOW.minus(ABANDON_AFTER).minusMillis(1)));
        given(executionReader.readPendingDispatches()).willReturn(List.of(stale));

        sut.dispatchPending();

        then(runQueuePublisher).should(never()).submit(any());
        then(runQueuePublisher).should().cancel(stale.execution().getId().value().toString());
        then(reservationManager).should().release(stale.execution());
    }

    @Test
    void 접수하고_꼭_한도만큼_지난_실행은_아직_버리지_않고_발행한다() {
        PendingDispatch edge = known(pending(NOW.minus(ABANDON_AFTER)));
        given(executionReader.readPendingDispatches()).willReturn(List.of(edge));

        sut.dispatchPending();

        then(runQueuePublisher).should().submit(edge.submit());
        then(reservationManager).should(never()).release(any());
    }

    // 버린 실행은 넘긴 것이 아니다. 넘긴 기록을 남기지 않는다.
    @Test
    void 한도가_지난_실행을_한_건_넘기려_하면_버리고_넘기지_않았다고_답한다() {
        PendingDispatch stale = known(pending(NOW.minus(ABANDON_AFTER).minusMillis(1)));
        ExecutionId id = stale.execution().getId();

        assertThat(sut.dispatch(id)).isFalse();

        then(reservationManager).should().release(stale.execution());
        then(runQueuePublisher).should(never()).submit(any());
        then(reservationManager).should(never()).markDispatched(any(), any());
    }

    @Test
    void 버리면서_취소를_걸지_못해도_돌려준다() {
        PendingDispatch stale = known(pending(NOW.minus(Duration.ofHours(1))));
        given(executionReader.readPendingDispatches()).willReturn(List.of(stale));
        willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "broker down"))
                .given(runQueuePublisher).cancel(any());

        sut.dispatchPending();

        then(reservationManager).should().release(stale.execution());
    }

    // 사용자 요청은 202 를 이미 받았다. 브로커가 느려도 요청 스레드가 붙잡히지 않는다.
    @Test
    void 접수가_커밋되면_요청_스레드를_붙잡지_않고_곧바로_발행을_시도한다() throws Exception {
        PendingDispatch pending = pending(NOW);
        ExecutionId id = pending.execution().getId();
        CountDownLatch brokerAnswers = new CountDownLatch(1);
        given(executionReader.findPendingDispatch(id)).willReturn(Optional.of(pending));
        willAnswer(invocation -> brokerAnswers.await(5, TimeUnit.SECONDS)).given(runQueuePublisher).submit(any());

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> sut.onAccepted(new ExecutionAccepted(id)));
        brokerAnswers.countDown();

        then(runQueuePublisher).should(timeout(3_000)).submit(pending.submit());
        then(reservationManager).should(timeout(3_000)).markDispatched(id, NOW);
    }

    // 발행 스레드가 살아 있다고 서버가 내려가지 못하면 안 된다. 스레드 덤프에서 알아볼 수 있는 이름을 단다.
    @Test
    void 커밋_직후_발행은_디스패치_이름을_단_데몬_스레드에서_한다() {
        PendingDispatch pending = pending(NOW);
        ExecutionId id = pending.execution().getId();
        AtomicReference<Thread> publishedOn = new AtomicReference<>();
        given(executionReader.findPendingDispatch(id)).willReturn(Optional.of(pending));
        willAnswer(invocation -> {
            publishedOn.set(Thread.currentThread());

            return null;
        }).given(runQueuePublisher).submit(any());

        sut.onAccepted(new ExecutionAccepted(id));

        then(reservationManager).should(timeout(3_000)).markDispatched(id, NOW);
        assertThat(publishedOn.get().isDaemon()).isTrue();
        assertThat(publishedOn.get().getName()).startsWith("execution-dispatch-");
    }

    // 서버가 내려가는 중이다. 남은 접수는 다음 기동의 주기 점검이 넘긴다.
    @Test
    void 내려간_뒤에_커밋된_접수는_넘기지_않는다() {
        ExecutionId id = ExecutionId.newId();
        sut.destroy();

        assertThatCode(() -> sut.onAccepted(new ExecutionAccepted(id))).doesNotThrowAnyException();

        then(executionReader).should(after(300).never()).findPendingDispatch(any());
    }

    // 주기 점검이 한 건씩 다시 확인하며 넘겨도 같은 것을 보게 한다.
    private PendingDispatch known(PendingDispatch pending) {
        given(executionReader.findPendingDispatch(pending.execution().getId())).willReturn(Optional.of(pending));

        return pending;
    }

    private static PendingDispatch pending(Instant acceptedAt) {
        ExecutionId id = ExecutionId.newId();
        Execution execution = Execution.restore(id, ExecutionRequestId.of(UUID.randomUUID()),
                WorkflowId.of(UUID.randomUUID()), UserId.of(1L), null, ExecutionStatus.QUEUED, 2, null, false,
                AbcAmount.of(new BigDecimal("6")));
        SubmitRun submit = new SubmitRun(id.value().toString(), RunMode.RUN, MAPPER.readTree("{\"id\":\"w1\"}"),
                null, null, false, List.of(), Map.of(), acceptedAt);

        return new PendingDispatch(execution, submit, acceptedAt);
    }
}
