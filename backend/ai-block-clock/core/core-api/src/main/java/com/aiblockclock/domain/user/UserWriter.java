package com.aiblockclock.domain.user;

import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class UserWriter {
    private final UserRepository userRepository;

    private final Clock clock;

    @Transactional
    public User create(String walletAddress) {
        String normalizedWalletAddress = walletAddress.toLowerCase(Locale.ROOT);

        UserEntity saved = userRepository.saveAndFlush(
                UserEntity.of(normalizedWalletAddress)
        );

        return UserEntityConverter.toDomain(saved);
    }

    // 가입 버튼을 빠르게 두 번 누르면 요청이 겹친다. 행을 잠근 뒤 확인해야 두 번째 요청이
    // 첫 번째의 결과를 보고 USER_006 으로 끝나며, 먼저 저장된 이름을 덮어쓰지 않는다.
    @Transactional
    public User completeOnboarding(UserId userId, UserSignup signup) {
        UserEntity entity = userRepository.findByIdForUpdate(userId.value())
                .orElseThrow(() -> new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIABLE));

        if (entity.getOnboardingCompletedAt() != null) {
            throw new CoreException(ErrorType.USER_ALREADY_ONBOARDED);
        }

        entity.completeOnboarding(signup.nickname().value(), signup.introduction().value(), clock.instant());

        return UserEntityConverter.toDomain(userRepository.saveAndFlush(entity));
    }
}
