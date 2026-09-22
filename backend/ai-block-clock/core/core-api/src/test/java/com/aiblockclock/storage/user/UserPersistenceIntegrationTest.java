package com.aiblockclock.storage.user;

import jakarta.persistence.EntityManager;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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
