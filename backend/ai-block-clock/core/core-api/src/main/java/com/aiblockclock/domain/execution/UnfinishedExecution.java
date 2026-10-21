package com.aiblockclock.domain.execution;

import java.time.Instant;

// 끝나지 않은 실행과 그 실행을 접수한 시각이다. 복구가 서버를 다시 띄운 뒤 도는 실행을 감시에 다시 붙이려고 읽는다.
public record UnfinishedExecution(Execution execution, Instant requestedAt) {
}
