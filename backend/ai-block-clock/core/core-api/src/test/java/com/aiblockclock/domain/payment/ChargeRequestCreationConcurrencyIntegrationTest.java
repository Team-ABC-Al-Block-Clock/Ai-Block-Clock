package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@Tag("integration")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:charge_creation_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1",
        ChargeChainFixtures.CHAIN_ID_PROPERTY,
        ChargeChainFixtures.CUSTODY_PROPERTY,
        ChargeChainFixtures.TOKEN_PROPERTY,
        ChargeChainFixtures.CONFIRMATIONS_PROPERTY
})
class ChargeRequestCreationConcurrencyIntegrationTest {

    private static final String TRANSACTION_HASH = "0x" + "a".repeat(64);
    private static final String OTHER_TRANSACTION_HASH = "0x" + "b".repeat(64);
    private static final String FIRST_WALLET = "0x1111111111111111111111111111111111111111";
    private static final String SECOND_WALLET = "0x4444444444444444444444444444444444444444";

    @Autowired
    private ChargeRequestService chargeRequestService;

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @MockitoSpyBean
    private ChargeRequestReader chargeRequestReader;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private ChargeRequestWriter chargeRequestWriter;

    private UserId firstUser;
    private UserId secondUser;

    @BeforeEach
    void 충전할_두_사용자를_만든다() {
        firstUser = UserId.of(userRepository.saveAndFlush(UserEntity.of(FIRST_WALLET)).getId());
        secondUser = UserId.of(userRepository.saveAndFlush(UserEntity.of(SECOND_WALLET)).getId());
    }

    @AfterEach
    void cleanUp() {
        chargeRequestRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void 같은_사용자의_동시_요청은_한_건만_저장하고_같은_ID를_반환한다() throws Exception {
        synchronizeInitialLookups();

        List<CreationOutcome> outcomes = runConcurrently(
                () -> create(firstUser, TRANSACTION_HASH),
                () -> create(firstUser, TRANSACTION_HASH)
        );

        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.errorType()).isNull();
            assertThat(outcome.request()).isNotNull();
        });
        assertThat(outcomes.get(0).request().getId()).isEqualTo(outcomes.get(1).request().getId());
        assertThat(chargeRequestRepository.count()).isEqualTo(1);
        assertThat(outcomes.get(0).request().getRequestedDepositAmount().value())
                .isEqualByComparingTo("1.000001");
    }

    @Test
    void 다른_사용자의_동시_요청은_저장된_요청의_소유자를_확인하고_재사용을_차단한다() throws Exception {
        synchronizeInitialLookups();

        List<CreationOutcome> outcomes = runConcurrently(
                () -> create(firstUser, TRANSACTION_HASH),
                () -> create(secondUser, TRANSACTION_HASH)
        );

        assertThat(outcomes.stream().filter(outcome -> outcome.request() != null).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(outcome -> outcome.errorType() != null)
                .map(CreationOutcome::errorType).toList())
                .containsExactly(ErrorType.CHARGE_TRANSACTION_ALREADY_USED);
        assertThat(chargeRequestRepository.count()).isEqualTo(1);
        ChargeRequest winner = outcomes.stream().map(CreationOutcome::request)
                .filter(request -> request != null).findFirst().orElseThrow();
        assertThat(chargeRequestRepository.findById(winner.getId()).orElseThrow().getUserReferenceId())
                .isEqualTo(winner.getUserReferenceId());
    }

    @Test
    void 서로_다른_거래의_동시_요청은_각각_정상_저장한다() throws Exception {
        synchronizeInitialLookups();

        List<CreationOutcome> outcomes = runConcurrently(
                () -> create(firstUser, TRANSACTION_HASH),
                () -> create(secondUser, OTHER_TRANSACTION_HASH)
        );

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.errorType()).isNull());
        assertThat(outcomes.get(0).request().getId()).isNotEqualTo(outcomes.get(1).request().getId());
        assertThat(chargeRequestRepository.count()).isEqualTo(2);
    }

    @Test
    void 중복_삽입의_롤백이_끝난_후_새_조회_트랜잭션에서_기존_요청을_반환한다() {
        ChargeRequest existingRequest = chargeRequestWriter.saveNewRequest(
                ChargeRequest.pending(Long.toString(firstUser.value()), FIRST_WALLET, ChargeChainFixtures.CHAIN_ID,
                        TRANSACTION_HASH,
                        DepositAmount.of(new BigDecimal("1.000001")))
        );
        AtomicBoolean insertRolledBack = new AtomicBoolean();
        AtomicInteger lookupCount = new AtomicInteger();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    insertRolledBack.set(status == STATUS_ROLLED_BACK);
                }
            });
            return invocation.callRealMethod();
        }).when(chargeRequestWriter).saveNewRequest(any());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            if (lookupCount.incrementAndGet() == 1) {
                return Optional.empty();
            }
            assertThat(insertRolledBack).isTrue();
            return invocation.callRealMethod();
        }).when(chargeRequestReader).findByChainAndTransactionHash(anyLong(), anyString());

        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        CreationOutcome outcome = create(firstUser, TRANSACTION_HASH);

        assertThat(outcome.errorType()).isNull();
        assertThat(outcome.request().getId()).isEqualTo(existingRequest.getId());
        assertThat(lookupCount).hasValue(2);
        assertThat(insertRolledBack).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(chargeRequestRepository.count()).isEqualTo(1);
    }

    private void synchronizeInitialLookups() {
        CyclicBarrier bothRequestsFoundNothing = new CyclicBarrier(2);
        AtomicInteger lookupCount = new AtomicInteger();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (lookupCount.incrementAndGet() <= 2) {
                assertThat(result).isEqualTo(Optional.empty());
                bothRequestsFoundNothing.await(10, TimeUnit.SECONDS);
            }
            return result;
        }).when(chargeRequestReader).findByChainAndTransactionHash(anyLong(), anyString());
    }

    private CreationOutcome create(UserId requester, String transactionHash) {
        try {
            ChargeRequest request = chargeRequestService.createChargeRequest(
                    requester, transactionHash, new BigDecimal("1.000001")
            );
            return new CreationOutcome(request, null);
        } catch (CoreException exception) {
            return new CreationOutcome(null, exception.getErrorType());
        }
    }

    private List<CreationOutcome> runConcurrently(
            Callable<CreationOutcome> first,
            Callable<CreationOutcome> second
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CreationOutcome> firstResult = executor.submit(first);
            Future<CreationOutcome> secondResult = executor.submit(second);
            return List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private record CreationOutcome(ChargeRequest request, ErrorType errorType) {
    }
}
