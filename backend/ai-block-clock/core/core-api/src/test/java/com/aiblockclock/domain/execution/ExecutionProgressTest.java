package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.enums.execution.ExecutionStatus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 구독자(SSE)와 조회가 받는 진행 상황이다. 이벤트의 snapshot 이나 우리가 남긴 상태로 만든다(B12 · B13 · B44).
class ExecutionProgressTest {
    private static final ExecutionId ID = ExecutionId.newId();

    // accepted 의 snapshot 은 아직 진행률을 세지 않아 total 이 0 이다. 그때는 우리가 아는 단계 수로 채운다.
    @Test
    void snapshot_의_total_이_0_이면_단계_수를_total_로_쓴다() {
        ExecutionProgress progress = ExecutionProgress.of(ID, ExecutionStatus.RUNNING, snapshot(0), 2);

        assertThat(progress.total()).isEqualTo(2);
    }

    // 분기 · 고쳐 쓰기로 엔진이 센 단계가 우리 단계 수와 다를 수 있다. 엔진이 센 것을 믿는다.
    @Test
    void snapshot_이_total_을_세었으면_단계_수가_아니라_그_값을_쓴다() {
        ExecutionProgress progress = ExecutionProgress.of(ID, ExecutionStatus.RUNNING, snapshot(3), 2);

        assertThat(progress.total()).isEqualTo(3);
    }

    // 진행 저장소에 없는 RUNNING 실행(재기동 직후)이다. 엔진에 묻지 않아 단계 목록이 비어 있다.
    @Test
    void 남긴_상태로_만든_RUNNING_진행_상황은_단계_수만_알고_단계_목록이_비어_있다() {
        ExecutionProgress progress = ExecutionProgress.recorded(ID, ExecutionStatus.RUNNING, 3);

        assertThat(progress.id()).isEqualTo(ID);
        assertThat(progress.status()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(progress.total()).isEqualTo(3);
        assertThat(progress.done()).isZero();
        assertThat(progress.percent()).isZero();
        assertThat(progress.steps()).isEmpty();
        assertThat(progress.failure()).isNull();
    }

    @Test
    void 남긴_상태로_만든_FAILED_진행_상황은_까닭을_알리지_않은_실패다() {
        ExecutionProgress progress = ExecutionProgress.recorded(ID, ExecutionStatus.FAILED, 3);

        assertThat(progress.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(progress.failure().errorCode()).isEqualTo("ABC_RUNTIME_UNREPORTED");
    }

    // 끝난 실행의 조회다(B44 "끝난 실행은 기존대로"). 엔진이 아직 들고 있는 단계는 보여 주되 상태는 우리가 닫은 것이다.
    @Test
    void 끝난_실행의_진행_상황은_엔진이_알린_단계에_우리가_닫은_상태를_입힌다() {
        ExecutionProgress engine = ExecutionProgress.of(ID, ExecutionStatus.RUNNING, snapshot(2), 2);

        ExecutionProgress concluded = ExecutionProgress.concluded(engine, ExecutionStatus.FAILED);

        assertThat(concluded.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(concluded.total()).isEqualTo(2);
        assertThat(concluded.currentStepName()).isNull();
        assertThat(concluded.failure().errorCode()).isEqualTo("ABC_RUNTIME_UNREPORTED");
    }

    @Test
    void 엔진이_알린_상태가_우리가_닫은_상태와_같으면_그대로_쓴다() {
        ExecutionProgress engine = ExecutionProgress.of(ID, ExecutionStatus.SUCCEEDED, snapshot(2), 2);

        assertThat(ExecutionProgress.concluded(engine, ExecutionStatus.SUCCEEDED)).isSameAs(engine);
    }

    private static RunStatus snapshot(int total) {
        return new RunStatus("run-1", "running", new RunStatus.Progress(0, total, 0, null, null),
                List.of(), List.of(), List.of(), null, null, false, null);
    }
}
