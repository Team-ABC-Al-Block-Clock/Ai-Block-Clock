package com.aiblockclock.domain.user;

import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

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
}
