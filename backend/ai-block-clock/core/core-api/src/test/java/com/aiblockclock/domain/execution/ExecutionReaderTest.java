package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunMessages;
import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.SubmitRun;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.storage.execution.ExecutionChargeRateEntity;
import com.aiblockclock.storage.execution.ExecutionChargeRateRepository;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionOutboxEntity;
import com.aiblockclock.storage.execution.ExecutionOutboxRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

// 디스패처 · 감시 · 복구가 읽는 실행의 상태다(B9 · B11 · B39 · B42). 저장소는 목이고, 쿼리 자체는 통합 테스트가 본다.
// 접수한 시각(createdAt)은 JPA 가 채우는 칸이라 엔티티를 spy 로 감싸 준다.
class ExecutionReaderTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant ACCEPTED_AT = Instant.parse("2026-10-16T05:00:00Z");
    private static final Instant DISPATCHED_AT = Instant.parse("2026-10-16T05:00:02Z");
    private static final UserId REQUESTER = UserId.of(7001L);

    private final ExecutionRepository executionRepository = mock(ExecutionRepository.class);
    private final ExecutionChargeRateRepository executionChargeRateRepository =
            mock(ExecutionChargeRateRepository.class);
    private final ExecutionOutboxRepository executionOutboxRepository = mock(ExecutionOutboxRepository.class);
    private final ExecutionReader sut = new ExecutionReader(executionRepository, executionChargeRateRepository,
            executionOutboxRepository);

    private final ExecutionId id = ExecutionId.newId();

    // 이벤트 적용 · dead letter 가 이 오류를 보고 모르는 실행으로 넘어간다(B21).
    @Test
    void 없는_실행을_읽으면_EXECUTION_NOT_FOUND_다() {
        given(executionRepository.findById(id.value())).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.read(id))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_NOT_FOUND));
    }

    @Test
    void 있는_실행을_읽으면_그_번호의_실행을_남긴_상태로_돌려준다() {
        given(executionRepository.findById(id.value()))
                .willReturn(Optional.of(entity(id.value(), id.value().toString(), ExecutionStatus.RUNNING)));

        Execution execution = sut.read(id);

        assertThat(execution.getId()).isEqualTo(id);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(execution.getRunId()).isEqualTo(RunId.of(id.value().toString()));
    }

    // 같은 요청 번호로 다시 온 접수가 앞 실행을 찾는다(B4).
    @Test
    void 같은_요청자와_요청_번호로_접수한_실행이_있으면_그_실행을_찾는다() {
        ExecutionEntity entity = entity(id.value(), null, ExecutionStatus.QUEUED);
        given(executionRepository.findByRequesterIdAndRequestId(REQUESTER.value(), entity.getRequestId()))
                .willReturn(Optional.of(entity));

        Optional<Execution> found = sut.find(REQUESTER, ExecutionRequestId.of(entity.getRequestId()));

        assertThat(found).hasValueSatisfying(execution -> assertThat(execution.getId()).isEqualTo(id));
    }

    @Test
    void 같은_요청자와_요청_번호로_접수한_실행이_없으면_비어_있다() {
        UUID requestId = UUID.randomUUID();
        given(executionRepository.findByRequesterIdAndRequestId(REQUESTER.value(), requestId))
                .willReturn(Optional.empty());

        assertThat(sut.find(REQUESTER, ExecutionRequestId.of(requestId))).isEmpty();
    }

    // 닫을 때 성공한 단계마다 받을 값이다(B14). 단가 칸이 생기기 전에 접수한 실행의 줄은 받을 값이 없어 싣지 않는다.
    @Test
    void 단계마다의_단가를_읽고_단가가_없는_옛_줄은_받을_값이_없는_단계로_둔다() {
        given(executionChargeRateRepository.findAllByExecutionId(id.value())).willReturn(List.of(
                new ExecutionChargeRateEntity(id.value(), "s1", new BigDecimal("1.0"), new BigDecimal("3")),
                new ExecutionChargeRateEntity(id.value(), "s0", new BigDecimal("1.0"), null)));

        StepPrices prices = sut.readStepPrices(id);

        assertThat(prices.of("s1")).hasValueSatisfying(step -> {
            assertThat(step.multiplier()).isEqualByComparingTo("1.0");
            assertThat(step.price().value()).isEqualByComparingTo("3");
        });
        assertThat(prices.of("s0")).isEmpty();
    }

    // 주기 점검이 발행해 볼 실행들이다(B9). 접수한 순서(outbox 순서)대로다.
    @Test
    void 넘기지_않은_실행들을_outbox_순서대로_읽는다() {
        ExecutionId later = ExecutionId.newId();
        ExecutionOutboxEntity first = outbox();
        ExecutionOutboxEntity second = new ExecutionOutboxEntity(later.value(), RunMessages.toJson(submit(later)));
        given(executionOutboxRepository.findUndispatched(argThat(statuses -> statuses != null
                && statuses.containsAll(List.of(ExecutionStatus.QUEUED, ExecutionStatus.RUNNING)))))
                .willReturn(List.of(first, second));
        given(executionRepository.findAllById(argThat(ids -> ids != null)))
                .willReturn(List.of(entity(later.value(), null, ExecutionStatus.QUEUED),
                        entity(id.value(), null, ExecutionStatus.QUEUED)));

        List<PendingDispatch> pending = sut.readPendingDispatches();

        assertThat(pending).extracting(found -> found.execution().getId()).containsExactly(id, later);
    }

    // 감시가 큐 대기 한도를 이 시각에서 잰다(B39).
    @Test
    void 넘긴_시각은_outbox_에_적힌_시각이다() {
        ExecutionOutboxEntity outbox = outbox();
        outbox.markDispatched(DISPATCHED_AT);
        given(executionOutboxRepository.findById(id.value())).willReturn(Optional.of(outbox));

        assertThat(sut.readDispatchedAt(id)).contains(DISPATCHED_AT);
    }

    @Test
    void 아직_넘기지_않은_실행은_넘긴_시각이_없다() {
        given(executionOutboxRepository.findById(id.value())).willReturn(Optional.of(outbox()));

        assertThat(sut.readDispatchedAt(id)).isEmpty();
    }

    // outbox 가 생기기 전에 접수한 실행이다.
    @Test
    void outbox_가_없는_실행은_넘긴_시각이_없다() {
        given(executionOutboxRepository.findById(id.value())).willReturn(Optional.empty());

        assertThat(sut.readDispatchedAt(id)).isEmpty();
    }

    // 복구가 서버를 다시 띄운 뒤 도는 실행을 감시에 다시 붙이려고 읽는다(B42).
    @Test
    void 끝나지_않은_실행을_접수한_시각과_함께_읽는다() {
        ExecutionEntity running = spy(entity(id.value(), id.value().toString(), ExecutionStatus.RUNNING));
        willReturn(ACCEPTED_AT).given(running).getCreatedAt();
        given(executionRepository.findAllByStatusIn(argThat(statuses -> statuses != null && statuses.size() == 2
                && statuses.containsAll(List.of(ExecutionStatus.QUEUED, ExecutionStatus.RUNNING)))))
                .willReturn(List.of(running));

        List<UnfinishedExecution> unfinished = sut.readUnfinished();

        assertThat(unfinished).singleElement().satisfies(found -> {
            assertThat(found.execution().getId()).isEqualTo(id);
            assertThat(found.execution().getStatus()).isEqualTo(ExecutionStatus.RUNNING);
            assertThat(found.requestedAt()).isEqualTo(ACCEPTED_AT);
        });
    }

    @Test
    void 넘기지_않은_실행은_outbox_의_SubmitRun_과_접수한_시각과_함께_읽는다() {
        ExecutionOutboxEntity outbox = spy(outbox());
        willReturn(ACCEPTED_AT).given(outbox).getCreatedAt();
        given(executionOutboxRepository.findById(id.value())).willReturn(Optional.of(outbox));
        given(executionRepository.findById(id.value()))
                .willReturn(Optional.of(entity(id.value(), null, ExecutionStatus.QUEUED)));

        Optional<PendingDispatch> pending = sut.findPendingDispatch(id);

        assertThat(pending).hasValueSatisfying(found -> {
            assertThat(found.execution().getId()).isEqualTo(id);
            assertThat(found.submit()).isEqualTo(submit(id));
            assertThat(found.acceptedAt()).isEqualTo(ACCEPTED_AT);
        });
    }

    @Test
    void 넘긴_시각이_적힌_실행은_넘기지_않은_실행이_아니다() {
        ExecutionOutboxEntity outbox = outbox();
        outbox.markDispatched(DISPATCHED_AT);
        given(executionOutboxRepository.findById(id.value())).willReturn(Optional.of(outbox));
        given(executionRepository.findById(id.value()))
                .willReturn(Optional.of(entity(id.value(), null, ExecutionStatus.QUEUED)));

        assertThat(sut.findPendingDispatch(id)).isEmpty();
    }

    // confirm 보다 이벤트가 먼저 와 runId 만 적혔다(B20). 이미 넘긴 실행이다.
    @Test
    void runId_가_적힌_실행은_넘기지_않은_실행이_아니다() {
        given(executionOutboxRepository.findById(id.value())).willReturn(Optional.of(outbox()));
        given(executionRepository.findById(id.value()))
                .willReturn(Optional.of(entity(id.value(), id.value().toString(), ExecutionStatus.RUNNING)));

        assertThat(sut.findPendingDispatch(id)).isEmpty();
    }

    // 넘기지 못한 채 버려진 실행이다(B10). outbox 의 넘긴 시각은 빈 채로 남는다.
    @Test
    void 끝난_실행은_넘기지_않은_실행이_아니다() {
        given(executionOutboxRepository.findById(id.value())).willReturn(Optional.of(outbox()));
        given(executionRepository.findById(id.value()))
                .willReturn(Optional.of(entity(id.value(), null, ExecutionStatus.FAILED)));

        assertThat(sut.findPendingDispatch(id)).isEmpty();
    }

    @Test
    void outbox_가_없는_실행은_넘기지_않은_실행이_아니다() {
        given(executionOutboxRepository.findById(id.value())).willReturn(Optional.empty());

        assertThat(sut.findPendingDispatch(id)).isEmpty();
    }

    private ExecutionOutboxEntity outbox() {
        return new ExecutionOutboxEntity(id.value(), RunMessages.toJson(submit(id)));
    }

    private static SubmitRun submit(ExecutionId of) {
        return new SubmitRun(of.value().toString(), RunMode.RUN, MAPPER.readTree("{\"id\":\"w1\",\"nodes\":[]}"),
                null, null, false, List.of(), Map.of(), ACCEPTED_AT);
    }

    private static ExecutionEntity entity(UUID id, String runId, ExecutionStatus status) {
        return new ExecutionEntity(id, UUID.randomUUID(), UUID.randomUUID(), REQUESTER.value(), runId, status, 2, null,
                null, null, false, null);
    }
}
