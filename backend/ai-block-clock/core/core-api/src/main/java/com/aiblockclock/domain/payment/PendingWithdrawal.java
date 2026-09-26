package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.identity.IdentityVerification;

// 관리자가 심사할 출금 신청과 신청한 사람의 본인 확인이다(ADM-001). 심사하려면 누가 받는지를 함께 봐야 한다.
// 본인 확인은 신청할 때 승인돼 있어야 해서 보통 있다. 본인 확인 검사가 생기기 전의 신청이면 비어 있다.
public record PendingWithdrawal(
        Withdrawal withdrawal,
        IdentityVerification identity
) {
}
