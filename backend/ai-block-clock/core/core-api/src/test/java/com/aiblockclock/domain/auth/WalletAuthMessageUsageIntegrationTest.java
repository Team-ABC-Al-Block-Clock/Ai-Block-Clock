package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.WalletAuthMessageEntity;
import com.aiblockclock.storage.auth.WalletAuthMessageRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
class WalletAuthMessageUsageIntegrationTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-09-17T00:00:00.123456Z");
    private static final Instant NOW = ISSUED_AT.plusSeconds(60).plusNanos(123);
    private static final Instant USED_AT = NOW.truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private WalletAuthMessageWriter writer;

    @Autowired
    private WalletAuthMessageReader reader;

    @Autowired
    private WalletAuthMessageRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Clock clock;

    private final List<String> walletAddresses = new ArrayList<>();

    @BeforeEach
    void 서버_시각을_고정한다() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void 테스트에서_발급한_메시지만_정리한다() {
        for (String walletAddress : walletAddresses) {
            jdbcTemplate.update("delete from wallet_auth_message where wallet_address = ?", walletAddress);
        }
    }

    @Test
    void 사용_처리는_서버_시각과_수정_시각을_기록하고_버전을_증가시킨다() {
        WalletAuthMessage issued = issueMessage();
        WalletAuthMessageEntity original = repository.findByWalletAddress(issued.walletAddress()).orElseThrow();

        writer.modifyAsUsed(issued);

        WalletAuthMessage used = reader.findByWalletAddress(issued.walletAddress()).orElseThrow();
        WalletAuthMessageEntity updated = repository.findByWalletAddress(issued.walletAddress()).orElseThrow();
        assertThat(used.usedAt()).isEqualTo(USED_AT);
        assertThat(used.nonce()).isEqualTo(issued.nonce());
        assertThat(used.message()).isEqualTo(issued.message());
        assertThat(updated.getUpdatedAt()).isEqualTo(USED_AT);
        assertThat(updated.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    void 이전에_읽은_미사용_객체를_다시_보내도_사용_기록을_덮어쓰지_못한다() {
        WalletAuthMessage issued = issueMessage();
        writer.modifyAsUsed(issued);
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));

        assertNotUsable(issued);

        assertThat(reader.findByWalletAddress(issued.walletAddress()).orElseThrow().usedAt()).isEqualTo(USED_AT);
        assertThat(repository.findByWalletAddress(issued.walletAddress()).orElseThrow().getVersion()).isEqualTo(1L);
    }

    @Test
    void 재발급_전의_nonce로는_새_메시지를_사용_처리하지_못한다() {
        WalletAuthMessage previous = issueMessage();
        WalletAuthMessage next = writer.append(new WalletAuthMessage(previous.walletAddress(), "b".repeat(64),
                "replacement message", USED_AT, USED_AT.plusSeconds(300), null));

        assertNotUsable(previous);

        assertThat(reader.findByWalletAddress(previous.walletAddress())).contains(next);
        assertThat(repository.findByWalletAddress(previous.walletAddress()).orElseThrow().getVersion()).isEqualTo(1L);
    }

    @Test
    void 삭제된_메시지는_사용_처리하지_못한다() {
        WalletAuthMessage issued = issueMessage();
        jdbcTemplate.update("delete from wallet_auth_message where wallet_address = ?", issued.walletAddress());

        assertNotUsable(issued);

        assertThat(reader.findByWalletAddress(issued.walletAddress())).isEmpty();
    }

    @Test
    void DB_수정_조건도_만료_시각에_도달한_메시지를_거절한다() {
        WalletAuthMessage issued = issueMessage();

        int updatedCount = new TransactionTemplate(transactionManager).execute(status ->
                repository.updateUsedAtIfUsable(issued.walletAddress(), issued.nonce(), issued.expiresAt()));

        assertThat(updatedCount).isZero();
        assertThat(reader.findByWalletAddress(issued.walletAddress())).contains(issued);
        assertThat(repository.findByWalletAddress(issued.walletAddress()).orElseThrow().getVersion()).isZero();
    }

    @Test
    void nonce가_같아도_다른_지갑의_메시지는_변경하지_않는다() {
        WalletAuthMessage first = issueMessage();
        WalletAuthMessage second = issueMessage();

        writer.modifyAsUsed(first);

        assertThat(reader.findByWalletAddress(first.walletAddress()).orElseThrow().usedAt()).isEqualTo(USED_AT);
        assertThat(reader.findByWalletAddress(second.walletAddress())).contains(second);
    }

    @Test
    void 같은_메시지의_동시_사용_요청은_하나만_성공한다() throws Exception {
        WalletAuthMessage issued = issueMessage();
        CyclicBarrier barrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> consumeOrConflict(issued, barrier));
            var second = executor.submit(() -> consumeOrConflict(issued, barrier));
            List<Object> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

            assertThat(results.stream().filter(Boolean.TRUE::equals).count()).isEqualTo(1L);
            List<CoreException> failures = results.stream().filter(CoreException.class::isInstance)
                    .map(CoreException.class::cast).toList();
            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst().getErrorType()).isEqualTo(ErrorType.WALLET_AUTH_MESSAGE_NOT_USABLE);
        }

        assertThat(reader.findByWalletAddress(issued.walletAddress()).orElseThrow().usedAt()).isEqualTo(USED_AT);
        assertThat(repository.findByWalletAddress(issued.walletAddress()).orElseThrow().getVersion()).isEqualTo(1L);
    }

    @Test
    void 함께_실행한_트랜잭션이_실패하면_사용_처리도_취소된다() {
        WalletAuthMessage issued = issueMessage();
        WalletAuthMessageEntity original = repository.findByWalletAddress(issued.walletAddress()).orElseThrow();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            writer.modifyAsUsed(issued);

            throw new CoreException(ErrorType.INTERNAL_ERROR);
        })).isInstanceOf(CoreException.class);

        assertThat(reader.findByWalletAddress(issued.walletAddress())).contains(issued);
        WalletAuthMessageEntity restored = repository.findByWalletAddress(issued.walletAddress()).orElseThrow();
        assertThat(restored.getVersion()).isZero();
        assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
    }

    @Test
    void 사용_전에_조회한_오래된_엔티티는_사용_기록을_지우지_못한다() throws Exception {
        WalletAuthMessage issued = issueMessage();
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch allowReissue = new CountDownLatch(1);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var staleReissue = executor.submit(() -> {
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        WalletAuthMessageEntity stale = repository.findByWalletAddress(issued.walletAddress()).orElseThrow();
                        loaded.countDown();
                        await(allowReissue);
                        stale.reissue("b".repeat(64), "stale replacement", USED_AT, USED_AT.plusSeconds(300));
                        repository.saveAndFlush(stale);
                    });

                    return Boolean.TRUE;
                } catch (OptimisticLockingFailureException exception) {
                    return exception;
                }
            });

            try {
                assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
                writer.modifyAsUsed(issued);
            } finally {
                allowReissue.countDown();
            }

            assertThat(staleReissue.get(20, TimeUnit.SECONDS)).isInstanceOf(OptimisticLockingFailureException.class);
        }

        WalletAuthMessage found = reader.findByWalletAddress(issued.walletAddress()).orElseThrow();
        assertThat(found.usedAt()).isEqualTo(USED_AT);
        assertThat(found.nonce()).isEqualTo(issued.nonce());
        assertThat(repository.findByWalletAddress(issued.walletAddress()).orElseThrow().getVersion()).isEqualTo(1L);
    }

    private Object consumeOrConflict(WalletAuthMessage issued, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            writer.modifyAsUsed(issued);

            return Boolean.TRUE;
        } catch (CoreException exception) {
            return exception;
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 요청 테스트의 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(exception);
        }
    }

    private void assertNotUsable(WalletAuthMessage issued) {
        assertThatThrownBy(() -> writer.modifyAsUsed(issued))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WALLET_AUTH_MESSAGE_NOT_USABLE));
    }

    private WalletAuthMessage issueMessage() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        walletAddresses.add(walletAddress);

        return writer.append(new WalletAuthMessage(walletAddress, "a".repeat(64), "original message",
                ISSUED_AT, ISSUED_AT.plusSeconds(300), null));
    }
}
