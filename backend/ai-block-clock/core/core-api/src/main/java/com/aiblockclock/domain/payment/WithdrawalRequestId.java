package com.aiblockclock.domain.payment;

import java.util.UUID;

// 화면이 출금 신청 버튼을 누를 때 만드는 번호다. 같은 번호가 다시 오면 새로 묶지 않고 앞의 신청을 돌려준다.
public record WithdrawalRequestId(UUID value) {
    public static WithdrawalRequestId of(UUID value) {
        return new WithdrawalRequestId(value);
    }
}
