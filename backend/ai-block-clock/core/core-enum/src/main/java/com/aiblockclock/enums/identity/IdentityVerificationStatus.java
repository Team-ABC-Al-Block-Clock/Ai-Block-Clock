package com.aiblockclock.enums.identity;

// 본인 확인(ACC-004)의 심사 상태다. 승인받은 사람만 정산금 출금을 신청할 수 있다.
// 반려되면 고쳐서 다시 제출할 수 있고, 승인된 뒤에는 바꾸지 않는다.
public enum IdentityVerificationStatus {
    SUBMITTED,
    APPROVED,
    REJECTED
}
