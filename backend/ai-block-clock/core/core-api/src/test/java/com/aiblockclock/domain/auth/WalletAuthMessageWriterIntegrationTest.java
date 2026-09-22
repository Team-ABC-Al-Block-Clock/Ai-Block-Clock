package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.WalletAuthMessageEntity;
import com.aiblockclock.storage.auth.WalletAuthMessageRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

@Tag("integration")
@SpringBootTest
class WalletAuthMessageWriterIntegrationTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-09-17T00:00:00.123456Z");

    @Autowired
    private WalletAuthMessageWriter writer;

    @Autowired
    private WalletAuthMessageReader reader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoSpyBean
    private WalletAuthMessageRepository repository;

    private final List<String> walletAddresses = new ArrayList<>();

    @AfterEach
    void 테스트에서_발급한_메시지만_정리한다() {
        for (String walletAddress : walletAddresses) {
            jdbcTemplate.update("delete from wallet_auth_message where wallet_address = ?", walletAddress);
        }
    }

    @Test
    void 처음_발급한_메시지는_소문자_주소로_저장된다() {
        String walletAddress = newWalletAddress();
        WalletAuthMessage issued = message(walletAddress.toUpperCase(Locale.ROOT), "a", ISSUED_AT);

        WalletAuthMessage saved = writer.append(issued);

        assertThat(saved.walletAddress()).isEqualTo(walletAddress);
        assertThat(saved.nonce()).isEqualTo(issued.nonce());
        assertThat(saved.message()).isEqualTo(issued.message());
        assertThat(saved.issuedAt()).isEqualTo(issued.issuedAt());
        assertThat(saved.expiresAt()).isEqualTo(issued.expiresAt());
        assertThat(saved.usedAt()).isNull();
        assertThat(reader.findByWalletAddress(walletAddress)).contains(saved);
        WalletAuthMessageEntity entity = repository.findByWalletAddress(walletAddress).orElseThrow();
        assertThat(entity.getId()).isPositive();
        assertThat(entity.getVersion()).isZero();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void 재발급하면_같은_행을_갱신하고_사용_기록을_비운다() {
        String walletAddress = newWalletAddress();
        writer.append(message(walletAddress, "a", ISSUED_AT));
        WalletAuthMessageEntity original = repository.findByWalletAddress(walletAddress).orElseThrow();
        markUsed(walletAddress);
        WalletAuthMessage next = message(walletAddress, "b", ISSUED_AT.plusSeconds(60));

        WalletAuthMessage saved = writer.append(next);

        WalletAuthMessageEntity updated = repository.findByWalletAddress(walletAddress).orElseThrow();
        assertThat(updated.getId()).isEqualTo(original.getId());
        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(updated.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(saved).isEqualTo(next);
        assertThat(reader.findByWalletAddress(walletAddress)).contains(next);
        assertRowCount(walletAddress);
    }

    @Test
    void 재발급_저장이_실패하면_기존_메시지와_사용_기록을_유지한다() {
        String walletAddress = newWalletAddress();
        writer.append(message(walletAddress, "a", ISSUED_AT));
        markUsed(walletAddress);
        WalletAuthMessage original = reader.findByWalletAddress(walletAddress).orElseThrow();
        WalletAuthMessage invalid = new WalletAuthMessage(walletAddress, null, "replacement",
                ISSUED_AT.plusSeconds(60), ISSUED_AT.plusSeconds(360), null);

        assertThatThrownBy(() -> writer.append(invalid)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(reader.findByWalletAddress(walletAddress)).contains(original);
        assertThat(repository.findByWalletAddress(walletAddress).orElseThrow().getVersion()).isZero();
    }

    @Test
    void 동시에_최초_발급하면_하나만_저장되고_나머지는_충돌_오류를_받는다() throws Exception {
        String walletAddress = newWalletAddress();
        synchronizeFirstTwoReads(walletAddress, false);

        WalletAuthMessage winner = issueConcurrently(walletAddress);

        assertThat(reader.findByWalletAddress(walletAddress)).contains(winner);
        assertThat(repository.findByWalletAddress(walletAddress).orElseThrow().getVersion()).isZero();
        assertRowCount(walletAddress);
    }

    @Test
    void 동시에_재발급하면_충돌한_요청이_성공한_메시지를_덮어쓰지_않는다() throws Exception {
        String walletAddress = newWalletAddress();
        writer.append(message(walletAddress, "a", ISSUED_AT));
        Long originalId = repository.findByWalletAddress(walletAddress).orElseThrow().getId();
        synchronizeFirstTwoReads(walletAddress, true);

        WalletAuthMessage winner = issueConcurrently(walletAddress);

        assertThat(reader.findByWalletAddress(walletAddress)).contains(winner);
        WalletAuthMessageEntity updated = repository.findByWalletAddress(walletAddress).orElseThrow();
        assertThat(updated.getId()).isEqualTo(originalId);
        assertThat(updated.getVersion()).isEqualTo(1L);
        assertRowCount(walletAddress);
    }

    private WalletAuthMessage issueConcurrently(String walletAddress) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> issueOrConflict(message(walletAddress, "b", ISSUED_AT.plusSeconds(60))));
            var second = executor.submit(() -> issueOrConflict(message(walletAddress, "c", ISSUED_AT.plusSeconds(90))));
            List<Object> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            List<WalletAuthMessage> successes = outcomes.stream().filter(WalletAuthMessage.class::isInstance)
                    .map(WalletAuthMessage.class::cast).toList();
            List<CoreException> conflicts = outcomes.stream().filter(CoreException.class::isInstance)
                    .map(CoreException.class::cast).toList();

            assertThat(successes).hasSize(1);
            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().getErrorType())
                    .isEqualTo(ErrorType.WALLET_AUTH_MESSAGE_CONCURRENTLY_MODIFIED);

            return successes.getFirst();
        }
    }

    private Object issueOrConflict(WalletAuthMessage message) {
        try {
            return writer.append(message);
        } catch (CoreException exception) {
            return exception;
        }
    }

    private void synchronizeFirstTwoReads(String walletAddress, boolean present) {
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(invocation -> {
            // Repository 인터페이스의 추상 메서드는 callRealMethod로 실행할 수 없다.
            // 두 Writer 트랜잭션에서 실제 DB를 조회한 뒤 저장 직전에 만나도록 한다.
            Optional<WalletAuthMessageEntity> found = entityManager.createQuery(
                    "select m from WalletAuthMessageEntity m where m.walletAddress = :walletAddress",
                    WalletAuthMessageEntity.class
            ).setParameter("walletAddress", walletAddress).getResultList().stream().findFirst();
            if (reads.incrementAndGet() <= 2) {
                assertThat(found.isPresent()).isEqualTo(present);
                barrier.await(10, TimeUnit.SECONDS);
            }

            return found;
        }).when(repository).findByWalletAddress(walletAddress);
    }

    private void markUsed(String walletAddress) {
        jdbcTemplate.update("update wallet_auth_message set used_at = ? where wallet_address = ?",
                Timestamp.from(ISSUED_AT.plusSeconds(30)), walletAddress);
    }

    private void assertRowCount(String walletAddress) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wallet_auth_message where wallet_address = ?", Long.class, walletAddress))
                .isEqualTo(1L);
    }

    private String newWalletAddress() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        walletAddresses.add(walletAddress);

        return walletAddress;
    }

    private WalletAuthMessage message(String walletAddress, String nonceCharacter, Instant issuedAt) {
        return new WalletAuthMessage(walletAddress, nonceCharacter.repeat(64), "message " + nonceCharacter,
                issuedAt, issuedAt.plusSeconds(300), null);
    }
}
