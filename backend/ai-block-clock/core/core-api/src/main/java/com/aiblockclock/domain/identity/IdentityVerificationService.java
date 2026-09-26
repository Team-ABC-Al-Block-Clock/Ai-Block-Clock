package com.aiblockclock.domain.identity;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdentityVerificationService {

    private final IdentityVerificationReader identityVerificationReader;
    private final IdentityVerificationWriter identityVerificationWriter;

    // 처음이면 새로 내고, 반려됐으면 고친 정보로 다시 낸다. 둘 다 심사 대기가 된다.
    @Transactional
    public IdentityVerification submit(UserId submitter, String legalName, LocalDate birthDate) {
        Optional<IdentityVerification> earlier = identityVerificationReader.find(submitter);

        if (earlier.isEmpty()) {
            return identityVerificationWriter.append(IdentityVerification.submitted(submitter, legalName, birthDate));
        }

        IdentityVerification verification = earlier.get();
        verification.resubmit(legalName, birthDate);

        return identityVerificationWriter.modifySubmission(verification);
    }

    public IdentityVerification readMine(UserId submitter) {
        return identityVerificationReader.find(submitter)
                .orElseThrow(() -> new CoreException(ErrorType.IDENTITY_VERIFICATION_NOT_FOUND));
    }

    public List<IdentityVerification> readAllSubmitted() {
        return identityVerificationReader.readAllSubmitted();
    }

    // 이미 승인했으면 그대로 돌려준다. 같은 승인이 다시 와도(재시도 · 연타) 실패로 보이지 않는다.
    @Transactional
    public IdentityVerification approve(UserId approver, Long verificationId) {
        IdentityVerification verification = identityVerificationReader.read(verificationId);

        if (verification.isApproved()) {
            return verification;
        }

        verification.approve(approver);

        return identityVerificationWriter.modifyReview(verification);
    }

    // 이미 반려했으면 그대로 돌려준다. 사유는 처음 반려한 것이 남는다.
    @Transactional
    public IdentityVerification reject(UserId rejecter, Long verificationId, String reason) {
        IdentityVerification verification = identityVerificationReader.read(verificationId);

        if (verification.isRejected()) {
            return verification;
        }

        verification.reject(rejecter, reason);

        return identityVerificationWriter.modifyReview(verification);
    }
}
