package com.aiblockclock.enums.payment;

import java.util.Arrays;
import java.util.List;

// 출금 신청의 상태다. 신청 · 사용자 취소(PAY-023), 관리자 승인 · 반려(PAY-024), 체인 지급(PAY-025)까지 있다.
// inProgress 는 아직 끝나지 않은 출금이다. 이런 출금이 있으면 새로 신청하지 못한다(PAY-027).
// countedForInterval 은 신청 주기에 세는 출금이다. 취소 · 반려처럼 돈이 나가지 않고 닫힌 것은 세지 않아 곧바로 다시 신청할 수 있다.
// 신청 주기는 "끝났지만 세는" 상태(지급 완료, PAY-025)가 생겨야 걸린다. 지금 세는 상태는 처리 중인 REQUESTED 뿐이라
// 처리 중인 출금 검사에서 먼저 막힌다. 새 상태를 더할 때 두 칸을 함께 정한다.
public enum WithdrawalStatus {
    REQUESTED(true, true),
    CANCELED(false, false),
    // 승인되어 지급을 기다린다. 금액은 지급이 끝날 때까지 묶여 있다
    APPROVED(true, true),
    REJECTED(false, false),
    // 서명한 전송을 체인에 보냈고 블록에 적히기를 기다린다. 거래 해시가 이미 적혀 있다
    PAYING(true, true),
    // 체인에서 전송이 성공했고 묶은 금액을 장부에서 내보냈다
    PAID(false, true);

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
