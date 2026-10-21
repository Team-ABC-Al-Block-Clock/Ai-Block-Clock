package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.SubmitRun;

import java.time.Instant;

// 아직 브로커로 넘기지 않은 실행이다(outbox 있음 · runId 없음 · 끝나지 않음). acceptedAt 은 접수한 시각이다.
public record PendingDispatch(Execution execution, SubmitRun submit, Instant acceptedAt) {
}
