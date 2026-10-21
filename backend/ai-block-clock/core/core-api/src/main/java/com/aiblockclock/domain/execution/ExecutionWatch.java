package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;

import java.time.Duration;
import java.time.Instant;

// 이벤트로 따라가는 실행 하나를 메모리에 든 것이다. 리스너 스레드(이벤트)와 감시 점검 스레드가 함께 만지므로 동기화한다.
//   heardAt       마지막으로 이벤트를 받은 시각. 엔진에 물었는데 닫지 않기로 했으면 그 시각이다. 기한은 여기서 잰다
//   lastSnapshot  마지막으로 받은 snapshot. 우리가 끊을 때(STALLED · LOST · dead letter) 어디까지 돌았는지의 근거다
//   attempt, seq  마지막으로 적용한 이벤트의 순번. 이보다 크지 않은 이벤트는 버린다. 아직 적용한 것이 없으면 0 이다
final class ExecutionWatch {
    private final ExecutionId id;
    private final String runId;
    private final int stepCount;
    private final Duration deadline;

    private Instant heardAt;
    private RunStatus lastSnapshot;
    private int attempt;
    private int seq;

    ExecutionWatch(ExecutionId id, int stepCount, Duration deadline, Instant heardAt) {
        this.id = id;
        this.runId = id.value().toString();
        this.stepCount = stepCount;
        this.deadline = deadline;
        this.heardAt = heardAt;
    }

    ExecutionId id() {
        return id;
    }

    // 런타임은 실행 번호를 run_id 로 쓴다 (ai/docs/integration-guide.md §4.3).
    String runId() {
        return runId;
    }

    int stepCount() {
        return stepCount;
    }

    synchronized RunStatus lastSnapshot() {
        return lastSnapshot;
    }

    // (attempt, seq) 를 사전식으로 비교한다. 다시 배달된 실행은 attempt 가 오르고 seq 를 1 부터 다시 센다.
    synchronized boolean isBehind(int candidateAttempt, int candidateSeq) {
        return candidateAttempt > attempt || candidateAttempt == attempt && candidateSeq > seq;
    }

    synchronized ExecutionWatch heard(int heardAttempt, int heardSeq, RunStatus snapshot, Instant now) {
        attempt = heardAttempt;
        seq = heardSeq;
        lastSnapshot = snapshot;
        heardAt = now;

        return this;
    }

    // 닫지 않기로 했다. 지금부터 같은 기한을 다시 잰다.
    synchronized void rearm(Instant now) {
        heardAt = now;
    }

    // 꼭 기한이 된 시각이면 넘긴 것으로 본다.
    synchronized boolean isOverdue(Instant now) {
        return !now.isBefore(heardAt.plus(deadline));
    }
}
