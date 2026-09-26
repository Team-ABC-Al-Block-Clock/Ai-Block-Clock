package com.aiblockclock.enums.payment;

import java.util.Arrays;
import java.util.List;

// 출금 신청의 상태다. 신청 · 사용자 취소(PAY-023)와 관리자 승인 · 반려(PAY-024)까지 있고, 지급(PAY-025)이 상태를 늘린다.
// inProgress 는 아직 끝나지 않은 출금이다. 이런 출금이 있으면 새로 신청하지 못한다(PAY-027).
// countedForInterval 은 신청 주기에 세는 출금이다. 취소 · 반려처럼 돈이 나가지 않고 닫힌 것은 세지 않아 곧바로 다시 신청할 수 있다.
public enum WithdrawalStatus {
    REQUESTED(true, true),
    CANCELED(false, false),
    // 승인되어 지급을 기다린다. 금액은 지급이 끝날 때까지 묶여 있다
    APPROVED(true, true),
    REJECTED(false, false);

    private final boolean inProgress;
    private final boolean countedForInterval;

    WithdrawalStatus(boolean inProgress, boolean countedForInterval) {
        this.inProgress = inProgress;
        this.countedForInterval = countedForInterval;
    }

    public static List<WithdrawalStatus> inProgressStatuses() {
        return Arrays.stream(values())
                .filter(status -> status.inProgress)
                .toList();
    }

    public static List<WithdrawalStatus> countedForIntervalStatuses() {
        return Arrays.stream(values())
                .filter(status -> status.countedForInterval)
                .toList();
    }
}
