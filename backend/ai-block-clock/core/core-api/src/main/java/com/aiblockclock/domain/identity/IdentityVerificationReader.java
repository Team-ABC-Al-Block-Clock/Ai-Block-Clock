package com.aiblockclock.domain.identity;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.identity.IdentityVerificationStatus;
import com.aiblockclock.storage.identity.IdentityVerificationRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdentityVerificationReader {

    private final IdentityVerificationRepository identityVerificationRepository;

    public IdentityVerification read(Long verificationId) {
        return identityVerificationRepository.findById(verificationId)
                .map(IdentityVerificationEntityConverter::toDomain)
                .orElseThrow(() -> new CoreException(ErrorType.IDENTITY_VERIFICATION_NOT_FOUND));
    }

    public Optional<IdentityVerification> find(UserId userId) {
        return identityVerificationRepository.findByUserId(userId.value())
                .map(IdentityVerificationEntityConverter::toDomain);
    }

    public boolean isApproved(UserId userId) {
        return identityVerificationRepository.existsByUserIdAndStatus(
                userId.value(), IdentityVerificationStatus.APPROVED);
    }

    // 오래 기다린 제출부터 준다. 관리자가 위에서부터 차례로 심사한다
    public List<IdentityVerification> readAllSubmitted() {
        return identityVerificationRepository
                .findAllByStatusOrderBySubmittedAtAscIdAsc(IdentityVerificationStatus.SUBMITTED).stream()
                .map(IdentityVerificationEntityConverter::toDomain)
                .toList();
    }

    public Map<UserId, IdentityVerification> readAllByUser(Collection<UserId> userIds) {
        return identityVerificationRepository.findAllByUserIdIn(userIds.stream().map(UserId::value).toList()).stream()
                .map(IdentityVerificationEntityConverter::toDomain)
                .collect(Collectors.toMap(IdentityVerification::getUserId, Function.identity()));
    }
}
