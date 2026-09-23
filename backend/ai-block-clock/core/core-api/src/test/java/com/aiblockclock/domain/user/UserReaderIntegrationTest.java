package com.aiblockclock.domain.user;

import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class UserReaderIntegrationTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserReader userReader;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 가입_전_사용자를_ID로_읽으면_이름이_비어_있고_가입_미완료다() {
        UserEntity saved = userRepository.saveAndFlush(
                UserEntity.of("0x6666666666666666666666666666666666666666"));
        entityManager.clear();

        User found = userReader.read(UserId.of(saved.getId()));

        assertThat(found.id()).isEqualTo(UserId.of(saved.getId()));
        assertThat(found.nickname()).isNull();
        assertThat(found.introduction()).isNull();
        assertThat(found.onboardingCompleted()).isFalse();
    }

    @Test
    void 가입한_사용자를_ID로_읽으면_이름_소개와_가입_완료가_담긴다() {
        UserEntity user = userRepository.saveAndFlush(
                UserEntity.of("0x7777777777777777777777777777777777777777"));
        Instant completedAt = Instant.parse("2026-09-23T01:02:03Z");
        user.completeOnboarding("김싸피", "에이전트를 만듭니다", completedAt);
        userRepository.flush();
        entityManager.clear();

        User found = userReader.read(UserId.of(user.getId()));

        assertThat(found.nickname()).isEqualTo("김싸피");
        assertThat(found.introduction()).isEqualTo("에이전트를 만듭니다");
        assertThat(found.onboardingCompletedAt()).isEqualTo(completedAt);
        assertThat(found.onboardingCompleted()).isTrue();
    }

    @Test
    void 없는_사용자_ID를_읽으면_요청자_정보가_올바르지_않다는_예외를_던진다() {
        assertThatThrownBy(() -> userReader.read(UserId.of(Long.MAX_VALUE)))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.REQUESTER_NOT_IDENTIFIABLE));
    }

    @Test
    void 지갑_주소로_사용자를_조회하면_도메인_모델을_반환한다() {
        String walletAddress = "0x3333333333333333333333333333333333333333";
        UserEntity saved = userRepository.saveAndFlush(UserEntity.of(walletAddress));
        entityManager.clear();

        User found = userReader.findByWalletAddress(walletAddress).orElseThrow();

        assertThat(found.id()).isEqualTo(UserId.of(saved.getId()));
        assertThat(found.walletAddress()).isEqualTo(walletAddress);
        assertThat(found.version()).isZero();
        assertThat(found.createdAt()).isNotNull();
        assertThat(found.updatedAt()).isNotNull();
    }

    @Test
    void 가입하지_않은_지갑을_조회하면_빈_결과를_반환한다() {
        Optional<User> found = userReader.findByWalletAddress(
                "0x4444444444444444444444444444444444444444");

        assertThat(found).isEmpty();
    }

    @Test
    void 소문자로_저장한_지갑을_대소문자가_섞인_주소로_조회한다() {
        String mixedCaseAddress = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
        String storedAddress = mixedCaseAddress.toLowerCase(Locale.ROOT);
        UserEntity saved = userRepository.saveAndFlush(UserEntity.of(storedAddress));
        entityManager.clear();

        User found = userReader.findByWalletAddress(mixedCaseAddress).orElseThrow();

        assertThat(found.id()).isEqualTo(UserId.of(saved.getId()));
        assertThat(found.walletAddress()).isEqualTo(storedAddress);
    }

    @Test
    void 대소문자가_섞여_저장된_지갑을_소문자_주소로_조회한다() {
        String storedAddress = "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359";
        UserEntity saved = userRepository.saveAndFlush(UserEntity.of(storedAddress));
        entityManager.clear();

        User found = userReader.findByWalletAddress(
                storedAddress.toLowerCase(Locale.ROOT)).orElseThrow();

        assertThat(found.id()).isEqualTo(UserId.of(saved.getId()));
        assertThat(found.walletAddress()).isEqualTo(storedAddress);
    }
}
