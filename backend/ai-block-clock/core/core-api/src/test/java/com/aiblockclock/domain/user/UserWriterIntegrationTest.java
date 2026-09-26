package com.aiblockclock.domain.user;

import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import jakarta.persistence.EntityManager;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@Tag("integration")
@SpringBootTest
@Transactional
class UserWriterIntegrationTest {
    @Autowired
    private UserWriter userWriter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 사용자를_생성하면_저장된_사용자_정보를_반환한다() {
        String walletAddress = "0x6666666666666666666666666666666666666666";

        User created = userWriter.create(walletAddress);
        entityManager.clear();

        UserEntity persisted = userRepository.findById(created.id().value()).orElseThrow();

        assertThat(created.id().value()).isPositive();
        assertThat(created.walletAddress()).isEqualTo(walletAddress);
        assertThat(created.version()).isZero();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();
        assertThat(persisted.getId()).isEqualTo(created.id().value());
        assertThat(persisted.getWalletAddress()).isEqualTo(created.walletAddress());
        assertThat(persisted.getVersion()).isEqualTo(created.version());
        assertThat(persisted.getCreatedAt())
                .isCloseTo(created.createdAt(), within(1, ChronoUnit.MICROS));
        assertThat(persisted.getUpdatedAt())
                .isCloseTo(created.updatedAt(), within(1, ChronoUnit.MICROS));
    }

    @Test
    void 대소문자가_섞인_지갑_주소는_소문자로_저장한다() {
        String walletAddress = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
        String expectedAddress = walletAddress.toLowerCase(Locale.ROOT);

        User created = userWriter.create(walletAddress);
        entityManager.clear();

        UserEntity persisted = userRepository.findById(created.id().value()).orElseThrow();

        assertThat(created.walletAddress()).isEqualTo(expectedAddress);
        assertThat(persisted.getWalletAddress()).isEqualTo(expectedAddress);
    }

    @Test
    void 가입을_마치면_이름_소개와_동의_시각과_가입_완료_시각을_저장한다() {
        User created = userWriter.create("0x9999999999999999999999999999999999999999");

        User onboarded = userWriter.completeOnboarding(created.id(), signup("김싸피"));
        entityManager.clear();

        UserEntity persisted = userRepository.findById(created.id().value()).orElseThrow();

        assertThat(onboarded.onboardingCompleted()).isTrue();
        assertThat(persisted.getNickname()).isEqualTo("김싸피");
        assertThat(persisted.getIntroduction()).isEqualTo("에이전트를 만듭니다");
        assertThat(persisted.getOnboardingCompletedAt()).isNotNull();
        assertThat(persisted.getTermsAgreedAt()).isEqualTo(persisted.getOnboardingCompletedAt());
        assertThat(persisted.getRecoveryNoticeConfirmedAt()).isEqualTo(persisted.getOnboardingCompletedAt());
    }

    @Test
    void 이미_가입한_사용자는_다시_가입할_수_없고_이름이_바뀌지_않는다() {
        User created = userWriter.create("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        userWriter.completeOnboarding(created.id(), signup("김싸피"));

        assertThatThrownBy(() -> userWriter.completeOnboarding(created.id(), signup("박싸피")))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_ONBOARDED));

        entityManager.clear();
        assertThat(userRepository.findById(created.id().value()).orElseThrow().getNickname()).isEqualTo("김싸피");
    }

    @Test
    void 없는_사용자는_가입할_수_없다() {
        assertThatThrownBy(() -> userWriter.completeOnboarding(UserId.of(Long.MAX_VALUE), signup("김싸피")))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.REQUESTER_NOT_IDENTIFIABLE));
    }

    @Test
    void 대소문자만_다른_같은_지갑으로_사용자를_중복_생성할_수_없다() {
        String walletAddress = "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359";
        userWriter.create(walletAddress);

        assertThatThrownBy(() -> userWriter.create(walletAddress.toLowerCase(Locale.ROOT)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasCauseInstanceOf(ConstraintViolationException.class)
                .satisfies(exception -> {
                    ConstraintViolationException cause =
                            (ConstraintViolationException) exception.getCause();

                    assertThat(cause.getConstraintName())
                            .containsIgnoringCase("uq_app_user_wallet_address");
                });
    }

    private static UserSignup signup(String nickname) {
        return new UserSignup(Nickname.of(nickname), Introduction.of("에이전트를 만듭니다"), true, true);
    }
}
