package com.aiblockclock.storage.user;

import jakarta.persistence.EntityManager;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class UserPersistenceIntegrationTest {
    @Autowired
    private EntityManager entityManager;

    @Test
    void 사용자를_저장하면_숫자_ID와_생성_수정_시각이_기록된다() {
        String walletAddress = "0x1111111111111111111111111111111111111111";
        UserEntity user = UserEntity.of(walletAddress);
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        UserEntity found = entityManager.find(UserEntity.class, user.getId());

        assertThat(found.getId()).isPositive();
        assertThat(found.getWalletAddress()).isEqualTo(walletAddress);
        assertThat(found.getVersion()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void 가입_전_사용자는_프로필과_동의_시각이_비어_있다() {
        UserEntity user = UserEntity.of("0x3333333333333333333333333333333333333333");
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        UserEntity found = entityManager.find(UserEntity.class, user.getId());

        assertThat(found.getNickname()).isNull();
        assertThat(found.getIntroduction()).isNull();
        assertThat(found.getTermsAgreedAt()).isNull();
        assertThat(found.getRecoveryNoticeConfirmedAt()).isNull();
        assertThat(found.getOnboardingCompletedAt()).isNull();
    }

    @Test
    void 가입을_마치면_프로필과_동의_시각과_가입_완료_시각이_저장된다() {
        UserEntity user = UserEntity.of("0x4444444444444444444444444444444444444444");
        entityManager.persist(user);
        entityManager.flush();

        Instant completedAt = Instant.parse("2026-09-23T01:02:03.123456Z");
        user.completeOnboarding("김싸피", "에이전트를 만듭니다", completedAt);
        entityManager.flush();
        entityManager.clear();

        UserEntity found = entityManager.find(UserEntity.class, user.getId());

        assertThat(found.getNickname()).isEqualTo("김싸피");
        assertThat(found.getIntroduction()).isEqualTo("에이전트를 만듭니다");
        assertThat(found.getTermsAgreedAt()).isEqualTo(completedAt);
        assertThat(found.getRecoveryNoticeConfirmedAt()).isEqualTo(completedAt);
        assertThat(found.getOnboardingCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void 같은_지갑_주소로_두_사용자를_저장할_수_없다() {
        String walletAddress = "0x2222222222222222222222222222222222222222";
        entityManager.persist(UserEntity.of(walletAddress));
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.persist(UserEntity.of(walletAddress));
            entityManager.flush();
        }).isInstanceOfSatisfying(ConstraintViolationException.class, exception ->
                assertThat(exception.getConstraintName())
                        .containsIgnoringCase("uq_app_user_wallet_address"));
    }
}
