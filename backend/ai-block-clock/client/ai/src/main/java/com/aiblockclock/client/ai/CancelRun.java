package com.aiblockclock.client.ai;

import java.time.Instant;

// 백엔드 → abc.control (fanout) 으로 보내는 취소다 (ai/docs/integration-guide.md §4.6).
public record CancelRun(String executionId, Instant requestedAt) {
}
