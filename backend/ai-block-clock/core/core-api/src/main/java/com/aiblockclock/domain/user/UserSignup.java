package com.aiblockclock.domain.user;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

// 화면이 버튼을 막는 것만으로는 동의를 받았다는 근거가 남지 않는다.
// 두 동의가 모두 있어야만 만들어지므로, 이 값을 받은 쪽은 동의를 다시 확인하지 않는다.
public record UserSignup(
        Nickname nickname,
        Introduction introduction,
        boolean termsAgreed,
        boolean recoveryNoticeConfirmed
) {
    public UserSignup {
        if (!termsAgreed) {
            throw new CoreException(ErrorType.USER_TERMS_NOT_AGREED);
        }

        if (!recoveryNoticeConfirmed) {
            throw new CoreException(ErrorType.USER_RECOVERY_NOTICE_NOT_CONFIRMED);
        }
    }
}
