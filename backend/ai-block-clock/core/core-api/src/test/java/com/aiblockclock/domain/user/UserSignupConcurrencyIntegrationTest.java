package com.aiblockclock.domain.user;

import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 두 요청이 각자 트랜잭션을 가져야 잠금이 실제로 부딪치므로 테스트 전체를 트랜잭션으로 감싸지 않는다.
@Tag("integration")
@SpringBootTest
class UserSignupConcurrencyIntegrationTest {
    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @AfterEach
    void 테스트에서_만든_사용자를_지운다() {
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    void 같은_사용자의_가입_요청이_동시에_오면_하나만_성공한다() throws Exception {
        userId = userRepository.saveAndFlush(UserEntity.of("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")).getId();
        CyclicBarrier barrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> signupOrFailure("김싸피", barrier));
            var second = executor.submit(() -> signupOrFailure("박싸피", barrier));
            List<Object> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

            List<UserProfile> successes = results.stream().filter(UserProfile.class::isInstance)
                    .map(UserProfile.class::cast).toList();
            List<CoreException> failures = results.stream().filter(CoreException.class::isInstance)
                    .map(CoreException.class::cast).toList();

            assertThat(successes).hasSize(1);
            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst().getErrorType()).isEqualTo(ErrorType.USER_ALREADY_ONBOARDED);
            assertThat(userRepository.findById(userId).orElseThrow().getNickname())
                    .isEqualTo(successes.getFirst().user().nickname());
        }
    }

    private Object signupOrFailure(String nickname, CyclicBarrier barrier) throws Exception {
        UserSignup signup = new UserSignup(Nickname.of(nickname), Introduction.of(null), true, true);
        barrier.await(10, TimeUnit.SECONDS);

        try {
            return userService.signup(UserId.of(userId), signup);
        } catch (CoreException exception) {
            return exception;
        }
    }
}
