package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunMessages;
import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.SubmitRun;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.enums.execution.ExecutionFault;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.storage.execution.ExecutionChargeRateEntity;
import com.aiblockclock.storage.execution.ExecutionChargeRateRepository;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionFailureEntity;
import com.aiblockclock.storage.execution.ExecutionFailureRepository;
import com.aiblockclock.storage.execution.ExecutionOutboxEntity;
import com.aiblockclock.storage.execution.ExecutionOutboxRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.execution.ExecutionSettlementEntity;
import com.aiblockclock.storage.execution.ExecutionSettlementRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

// 실행 행을 잠그고 보는 판단(B10 · B11 · B13 · B19)과 접수 · 닫기 때 무엇을 적는지(B2 · B5 · B14 · B15)다.
// 저장소는 목이고, 잠금 · 제약 · 쿼리 자체는 ExecutionReservationManagerIntegrationTest 가 DB 위에서 본다.
class ExecutionWriterTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final UserId REQUESTER = UserId.of(7001L);
    private static final Instant DISPATCHED_AT = Instant.parse("2026-10-16T05:00:02Z");

    private final ExecutionRepository executionRepository = mock(ExecutionRepository.class);
    private final ExecutionChargeRateRepository executionChargeRateRepository =
            mock(ExecutionChargeRateRepository.class);
    private final ExecutionFailureRepository executionFailureRepository = mock(ExecutionFailureRepository.class);
    private final ExecutionSettlementRepository executionSettlementRepository =
            mock(ExecutionSettlementRepository.class);
    private final ExecutionOutboxRepository executionOutboxRepository = mock(ExecutionOutboxRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final ExecutionWriter sut = new ExecutionWriter(executionRepository, executionChargeRateRepository,
            executionFailureRepository, executionSettlementRepository, executionOutboxRepository, events);

    private final ExecutionId id = ExecutionId.newId();
    private final RunId runId = RunId.of(id.value().toString());

    // 접수와 같은 트랜잭션에 보낼 본문을 적고, 커밋되면 디스패처가 곧바로 넘기게 알린다(B2 · B5).
    @Test
    void outbox_에_그_실행의_SubmitRun_본문을_적고_접수했다고_알린다() {
        SubmitRun submit = new SubmitRun(id.value().toString(), RunMode.RUN,
                MAPPER.readTree("{\"id\":\"w1\",\"nodes\":[]}"), null, "비교해 주세요", false, List.of(), Map.of(),
                Instant.parse("2026-10-16T05:00:00Z"));

        sut.appendOutbox(id, submit);

        ArgumentCaptor<ExecutionOutboxEntity> saved = ArgumentCaptor.forClass(ExecutionOutboxEntity.class);
        then(executionOutboxRepository).should().save(saved.capture());
        assertThat(saved.getValue().getExecutionId()).isEqualTo(id.value());
        assertThat(saved.getValue().getPayload()).isEqualTo(RunMessages.toJson(submit));
        assertThat(saved.getValue().getDispatchedAt()).isNull();
        then(events).should().publishEvent(new ExecutionAccepted(id));
    }

    // B7 · B11 — 넘긴 기록(runId)은 행을 잠근 뒤 한 번만 적는다.
    @Test
    void 넘긴_기록이_없는_대기_중인_실행에는_실행_번호를_runId_로_적고_적었다고_답한다() {
        ExecutionEntity entity = entity(null, ExecutionStatus.QUEUED);
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.of(entity));

        assertThat(sut.markDispatched(id, runId)).isTrue();

        assertThat(entity.getRunId()).isEqualTo(id.value().toString());
    }

    // confirm 과 이벤트 · 주기 점검이 겹쳤다. 먼저 적힌 기록을 덮어쓰지 않는다.
    @Test
    void 이미_넘긴_실행이면_runId_를_덮어쓰지_않고_적지_않았다고_답한다() {
        ExecutionEntity entity = entity("other", ExecutionStatus.QUEUED);
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.of(entity));

        assertThat(sut.markDispatched(id, runId)).isFalse();

        assertThat(entity.getRunId()).isEqualTo("other");
    }

    // 넘기지 못해 버려진 실행에 늦게 온 confirm 이다(B11). 넘긴 기록을 되살리지 않는다.
    @Test
    void 끝난_실행이면_runId_를_적지_않고_적지_않았다고_답한다() {
        ExecutionEntity entity = entity(null, ExecutionStatus.FAILED);
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.of(entity));

        assertThat(sut.markDispatched(id, runId)).isFalse();

        assertThat(entity.getRunId()).isNull();
    }

    @Test
    void 없는_실행에_넘긴_기록을_남기려_하면_EXECUTION_NOT_FOUND_다() {
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.markDispatched(id, runId))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_NOT_FOUND));
    }

    // 감시가 큐 대기 한도를 이 시각에서 잰다(B7 · B39).
    @Test
    void outbox_에_넘긴_시각을_적는다() {
        ExecutionOutboxEntity outbox = new ExecutionOutboxEntity(id.value(), "{}");
        given(executionOutboxRepository.findById(id.value())).willReturn(Optional.of(outbox));

        sut.markOutboxDispatched(id, DISPATCHED_AT);

        assertThat(outbox.getDispatchedAt()).isEqualTo(DISPATCHED_AT);
    }

    @Test
    void outbox_가_없는_옛_실행이면_넘긴_시각을_적지_않고_던지지도_않는다() {
        given(executionOutboxRepository.findById(id.value())).willReturn(Optional.empty());

        assertThatCode(() -> sut.markOutboxDispatched(id, DISPATCHED_AT)).doesNotThrowAnyException();
    }

    // 커밋 직후 시도가 늦게 넘긴 실행을 디스패처가 넘기지 못한 것으로 보고 닫으면 안 된다(B10 · B11).
    @Test
    void 이미_넘긴_실행은_넘기지_못한_실행으로_닫지_않는다() {
        ExecutionEntity entity = entity(id.value().toString(), ExecutionStatus.QUEUED);
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.of(entity));

        Optional<Execution> closed = sut.finishUndispatched(id, ExecutionStatus.FAILED, ExecutionFailure.undispatched());

        assertThat(closed).isEmpty();
        assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        then(executionFailureRepository).shouldHaveNoInteractions();
    }

    @Test
    void 넘기지_못한_대기_중인_실행은_FAILED_로_닫고_닿지_못한_실패로_적는다() {
        ExecutionEntity entity = entity(null, ExecutionStatus.QUEUED);
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.of(entity));

        Optional<Execution> closed = sut.finishUndispatched(id, ExecutionStatus.FAILED, ExecutionFailure.undispatched());

        assertThat(closed).hasValueSatisfying(execution -> {
            assertThat(execution.getId()).isEqualTo(id);
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        });
        assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(savedFailure()).satisfies(failure -> {
            assertThat(failure.getExecutionId()).isEqualTo(id.value());
            assertThat(failure.getFailureCode()).isEqualTo("ABC_RUNTIME_UNREACHABLE");
            assertThat(failure.getFault()).isEqualTo(ExecutionFault.PLATFORM);
        });
    }

    @Test
    void 없는_실행을_넘기지_못한_실행으로_닫으려_하면_EXECUTION_NOT_FOUND_다() {
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.finishUndispatched(id, ExecutionStatus.FAILED, ExecutionFailure.undispatched()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_NOT_FOUND));
    }

    // 안전망 B9b — 안전망이 QUEUED 로 다시 읽은 뒤 닫기 전에 accepted 가 와서 RUNNING 이 됐다. 잠근 뒤 보고 닫지 않는다.
    // 판정도 적지 않는다. 그 실행은 감시가 맡는다.
    @Test
    void 잠근_뒤_RUNNING_이_된_실행은_큐_대기_안전망으로_닫지_않는다() {
        ExecutionEntity entity = entity(id.value().toString(), ExecutionStatus.RUNNING);
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.of(entity));

        Optional<Execution> closed = sut.finishQueued(id, ExecutionStatus.FAILED, queueExpired());

        assertThat(closed).isEmpty();
        assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        then(executionFailureRepository).shouldHaveNoInteractions();
    }

    // 안전망 B6 · B9b
    @Test
    void 잠근_뒤에도_QUEUED_인_실행은_큐_대기_안전망이_FAILED_로_닫고_큐_만료로_적는다() {
        ExecutionEntity entity = entity(id.value().toString(), ExecutionStatus.QUEUED);
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.of(entity));

        Optional<Execution> closed = sut.finishQueued(id, ExecutionStatus.FAILED, queueExpired());

        assertThat(closed).hasValueSatisfying(execution -> {
            assertThat(execution.getId()).isEqualTo(id);
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        });
        assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(savedFailure()).satisfies(failure -> {
            assertThat(failure.getExecutionId()).isEqualTo(id.value());
            assertThat(failure.getFailureCode()).isEqualTo("ABC_QUEUE_EXPIRED");
            assertThat(failure.getFault()).isEqualTo(ExecutionFault.PLATFORM);
        });
    }

    // 안전망 B9 — dead letter 가 먼저 닫았다. 다시 닫지도 판정을 덮어쓰지도 않는다.
    @Test
    void 이미_끝난_실행은_큐_대기_안전망으로_다시_닫지_않는다() {
        ExecutionEntity entity = entity(id.value().toString(), ExecutionStatus.FAILED);
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.of(entity));

        Optional<Execution> closed = sut.finishQueued(id, ExecutionStatus.FAILED, queueExpired());

        assertThat(closed).isEmpty();
        assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        then(executionFailureRepository).shouldHaveNoInteractions();
    }

    @Test
    void 없는_실행을_큐_대기_안전망으로_닫으려_하면_EXECUTION_NOT_FOUND_다() {
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.finishQueued(id, ExecutionStatus.FAILED, queueExpired()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_NOT_FOUND));
    }

    // accepted · progress 가 실행을 RUNNING 으로 옮긴다(B12 · B13).
    @Test
    void 대기_중인_실행을_RUNNING_으로_옮긴다() {
        ExecutionEntity entity = entity(id.value().toString(), ExecutionStatus.QUEUED);
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.of(entity));

        sut.moveTo(id, ExecutionStatus.RUNNING);

        assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    }

    // 이벤트를 적용하는 사이 감시 · dead letter 가 먼저 닫았다(B19).
    @Test
    void 끝난_실행은_RUNNING_으로_되돌리지_않는다() {
        ExecutionEntity entity = entity(id.value().toString(), ExecutionStatus.SUCCEEDED);
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.of(entity));

        sut.moveTo(id, ExecutionStatus.RUNNING);

        assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    }

    // 이벤트 · 감시 · dead letter 가 같은 실행을 겹쳐 닫아도 한 번만 닫힌다(B19 · B33).
    @Test
    void 이미_끝난_실행은_다시_닫지_않고_실패도_적지_않는다() {
        ExecutionEntity entity = entity(id.value().toString(), ExecutionStatus.SUCCEEDED);
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.of(entity));

        Optional<Execution> closed = sut.finish(id, ExecutionStatus.FAILED,
                new ExecutionFailure(null, "ABC_RUNTIME_STALLED", ExecutionFault.PLATFORM, false, null, null));

        assertThat(closed).isEmpty();
        assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        then(executionFailureRepository).shouldHaveNoInteractions();
    }

    @Test
    void 성공으로_닫으면_SUCCEEDED_로_옮긴_실행을_돌려주고_실패는_적지_않는다() {
        ExecutionEntity entity = entity(id.value().toString(), ExecutionStatus.RUNNING);
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.of(entity));

        Optional<Execution> closed = sut.finish(id, ExecutionStatus.SUCCEEDED, null);

        assertThat(closed).hasValueSatisfying(execution -> {
            assertThat(execution.getId()).isEqualTo(id);
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        });
        assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        then(executionFailureRepository).shouldHaveNoInteractions();
    }

    // dead letter · 기한 초과처럼 우리가 끊은 실패는 공급자도 에이전트도 없다(B30).
    @Test
    void 플랫폼_탓_실패는_멎은_단계와_코드를_적고_공급자와_에이전트는_비워_둔다() {
        given(executionRepository.findForUpdate(id.value()))
                .willReturn(Optional.of(entity(id.value().toString(), ExecutionStatus.RUNNING)));

        sut.finish(id, ExecutionStatus.FAILED,
                new ExecutionFailure("n2", "ABC_QUEUE_EXPIRED", ExecutionFault.PLATFORM, false, null, null));

        assertThat(savedFailure()).satisfies(failure -> {
            assertThat(failure.getExecutionId()).isEqualTo(id.value());
            assertThat(failure.getFailedStepId()).isEqualTo("n2");
            assertThat(failure.getFailureCode()).isEqualTo("ABC_QUEUE_EXPIRED");
            assertThat(failure.getFault()).isEqualTo(ExecutionFault.PLATFORM);
            assertThat(failure.isAffectsSuccessRate()).isFalse();
            assertThat(failure.getSupplierId()).isNull();
            assertThat(failure.getAgentId()).isNull();
        });
    }

    // failed 이벤트의 snapshot 이 공급자 탓으로 판정한 실패다(B15).
    @Test
    void 공급자_탓_실패는_그_단계의_공급자와_에이전트를_함께_적는다() {
        AgentId agent = AgentId.newId();
        given(executionRepository.findForUpdate(id.value()))
                .willReturn(Optional.of(entity(id.value().toString(), ExecutionStatus.RUNNING)));

        sut.finish(id, ExecutionStatus.FAILED,
                new ExecutionFailure("a1.n2", "MODEL_TIMEOUT", ExecutionFault.SUPPLIER, true, UserId.of(8001L), agent));

        assertThat(savedFailure()).satisfies(failure -> {
            assertThat(failure.getFailedStepId()).isEqualTo("a1.n2");
            assertThat(failure.getFault()).isEqualTo(ExecutionFault.SUPPLIER);
            assertThat(failure.isAffectsSuccessRate()).isTrue();
            assertThat(failure.getSupplierId()).isEqualTo(8001L);
            assertThat(failure.getAgentId()).isEqualTo(agent.value());
        });
    }

    @Test
    void 없는_실행을_닫으려_하면_EXECUTION_NOT_FOUND_다() {
        given(executionRepository.findForUpdate(id.value())).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.finish(id, ExecutionStatus.SUCCEEDED, null))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_NOT_FOUND));
    }

    // B1 — 접수한 실행을 적는다.
    @Test
    void 새_실행을_적으면_넘긴_실행_번호와_예치_금액으로_적힌_실행을_돌려준다() {
        given(executionRepository.saveAndFlush(any(ExecutionEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Execution appended = sut.append(requested());

        assertThat(appended.getId()).isEqualTo(id);
        assertThat(appended.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(appended.getReserved()).isEqualTo(AbcAmount.of(new BigDecimal("6")));
    }

    // 같은 요청 번호가 동시에 두 번 들어와 늦은 쪽이 유일 제약에 걸렸다(B4). DB 는 제약 이름을 대문자로 줄 수 있다.
    @Test
    void 같은_요청_번호의_유일_제약에_걸리면_EXECUTION_ALREADY_REQUESTED_다() {
        given(executionRepository.saveAndFlush(any(ExecutionEntity.class)))
                .willThrow(violationOf("UQ_EXECUTION_REQUESTER_REQUEST"));

        assertThatThrownBy(() -> sut.append(requested()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_ALREADY_REQUESTED));
    }

    @Test
    void 다른_제약에_걸리면_받은_예외를_그대로_던진다() {
        DataIntegrityViolationException violation = violationOf("fk_execution_workflow");
        given(executionRepository.saveAndFlush(any(ExecutionEntity.class))).willThrow(violation);

        assertThatThrownBy(() -> sut.append(requested())).isSameAs(violation);
    }

    @Test
    void 제약_이름을_모르는_위반이면_받은_예외를_그대로_던진다() {
        DataIntegrityViolationException violation = violationOf(null);
        given(executionRepository.saveAndFlush(any(ExecutionEntity.class))).willThrow(violation);

        assertThatThrownBy(() -> sut.append(requested())).isSameAs(violation);
    }

    @Test
    void 제약_위반이_원인에_없으면_받은_예외를_그대로_던진다() {
        DataIntegrityViolationException violation =
                new DataIntegrityViolationException("value too long", new SQLException("data truncation"));
        given(executionRepository.saveAndFlush(any(ExecutionEntity.class))).willThrow(violation);

        assertThatThrownBy(() -> sut.append(requested())).isSameAs(violation);
    }

    // 끝난 뒤 받을 값은 접수할 때 정한 단계마다의 단가다(B14).
    @Test
    void 단계마다_배율과_단가를_적는다() {
        sut.appendStepPrices(id, new StepPrices(Map.of(
                "s1", new StepPrice(new BigDecimal("1.3000"), AbcAmount.of(new BigDecimal("0.013"))))));

        ArgumentCaptor<List<ExecutionChargeRateEntity>> saved = ArgumentCaptor.captor();
        then(executionChargeRateRepository).should().saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(rate -> {
            assertThat(rate.getExecutionId()).isEqualTo(id.value());
            assertThat(rate.getStepId()).isEqualTo("s1");
            assertThat(rate.getPriceMultiplier()).isEqualByComparingTo("1.3");
            assertThat(rate.getUnitPrice()).isEqualByComparingTo("0.013");
        });
    }

    // 캔버스에 바로 놓인 노드는 에이전트가 없다(ExecutionSettlement).
    @Test
    void 정산_내역은_단계마다_공급자와_금액을_적고_에이전트_밖_단계는_에이전트를_비워_둔다() {
        AgentId agent = AgentId.newId();

        sut.appendSettlements(id, List.of(
                new ExecutionSettlement("n1", "성적 분석", UserId.of(8001L), null, AbcAmount.of(new BigDecimal("2"))),
                new ExecutionSettlement("a1.n1", "가정통신문 작성", UserId.of(8002L), agent,
                        AbcAmount.of(new BigDecimal("3")))));

        ArgumentCaptor<List<ExecutionSettlementEntity>> saved = ArgumentCaptor.captor();
        then(executionSettlementRepository).should().saveAll(saved.capture());
        assertThat(saved.getValue()).satisfiesExactly(
                outside -> {
                    assertThat(outside.getExecutionId()).isEqualTo(id.value());
                    assertThat(outside.getStepId()).isEqualTo("n1");
                    assertThat(outside.getNodeName()).isEqualTo("성적 분석");
                    assertThat(outside.getSupplierId()).isEqualTo(8001L);
                    assertThat(outside.getAgentId()).isNull();
                    assertThat(outside.getAmountAbc()).isEqualByComparingTo("2");
                },
                inside -> {
                    assertThat(inside.getStepId()).isEqualTo("a1.n1");
                    assertThat(inside.getSupplierId()).isEqualTo(8002L);
                    assertThat(inside.getAgentId()).isEqualTo(agent.value());
                    assertThat(inside.getAmountAbc()).isEqualByComparingTo("3");
                });
    }

    private ExecutionFailureEntity savedFailure() {
        ArgumentCaptor<ExecutionFailureEntity> saved = ArgumentCaptor.forClass(ExecutionFailureEntity.class);
        then(executionFailureRepository).should().save(saved.capture());
        return saved.getValue();
    }

    private Execution requested() {
        return Execution.requested(id, ExecutionRequestId.of(UUID.randomUUID()), WorkflowId.of(UUID.randomUUID()),
                        REQUESTER, 2, null, false)
                .reserve(AbcAmount.of(new BigDecimal("6")));
    }

    private static ExecutionFailure queueExpired() {
        return new ExecutionFailure(null, "ABC_QUEUE_EXPIRED", ExecutionFault.PLATFORM, false, null, null);
    }

    private ExecutionEntity entity(String runIdValue, ExecutionStatus status) {
        return new ExecutionEntity(id.value(), UUID.randomUUID(), UUID.randomUUID(), REQUESTER.value(), runIdValue,
                status, 2, null, null, null, false, null);
    }

    private static DataIntegrityViolationException violationOf(String constraintName) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("constraint violated", new SQLException("violated"), constraintName));
    }
}
