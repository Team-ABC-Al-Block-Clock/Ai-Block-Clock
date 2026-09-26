package com.aiblockclock.domain.payment;

import java.time.Instant;

// 새 신청을 정책에 비춰 볼 때 필요한 지난 출금의 요약이다.
// lastRequestedAt 은 신청 주기에 세는 출금 가운데 가장 늦은 신청 시각이다. 없으면 비어 있다.
public record RecentWithdrawals(
        boolean anyInProgress,
        Instant lastRequestedAt
) {
}
