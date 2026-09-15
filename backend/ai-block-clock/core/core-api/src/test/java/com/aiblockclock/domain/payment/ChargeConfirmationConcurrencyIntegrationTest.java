package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@Tag("integration")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:charge_confirmation_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class ChargeConfirmationConcurrencyIntegrationTest {

    private static final BigDecimal CHARGE_AMOUNT = new BigDecimal("1.000001");
    private static final String SYSTEM_REFERENCE_ID = "SYSTEM";

    @Autowired
    private ChargeConfirmationManager chargeConfirmationManager;

    @Autowired
    private ChargeRequestReader chargeRequestReader;

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @MockitoSpyBean
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void cleanUp() {
        // All rows belong to this class's separate in-memory test database.
        ledgerEntryRepository.deleteAllInBatch();
        ledgerTransactionRepository.deleteAllInBatch();
        ledgerAccountRepository.deleteAllInBatch();
        chargeRequestRepository.deleteAllInBatch();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 8})
    void 서로_다른_사용자의_동시_충전은_SYSTEM_잔액에_모두_합산된다(int concurrentCharges) throws Exception {
        createSystemAccount();
        List<ChargeRequest> requests = IntStream.range(0, concurrentCharges)
                .mapToObj(index -> saveRequest("user-" + index, index)).toList();

        confirmConcurrently(requests);

        assertAllCreditsPersisted(requests);
        assertThat(ledgerAccountRepository.count()).isEqualTo(concurrentCharges + 1);
    }

    @Test
    void SYSTEM이_없는_상태에서_동시에_충전해도_계정은_한_건만_생성된다() throws Exception {
        List<ChargeRequest> requests = List.of(saveRequest("first-user", 0), saveRequest("second-user", 1));

        confirmConcurrently(requests);

        assertAllCreditsPersisted(requests);
        assertThat(ledgerAccountRepository.count()).isEqualTo(3);
    }

    @Test
    void 같은_사용자의_서로_다른_거래도_잔액에_모두_합산된다() throws Exception {
        List<ChargeRequest> requests = List.of(saveRequest("same-user", 0), saveRequest("same-user", 1));

        confirmConcurrently(requests);

        assertAllCreditsPersisted(requests);
        assertThat(ledgerAccountRepository.count()).isEqualTo(2);
        assertThat(ledgerAccountRepository.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_BALANCE, "same-user").orElseThrow().getBalance())
                .isEqualByComparingTo(CHARGE_AMOUNT.multiply(BigDecimal.valueOf(2)));
    }

    @Test
    void 원장_항목_저장이_실패하면_확정과_모든_잔액_갱신도_롤백된다() {
        LedgerAccountEntity system = createSystemAccount();
        ChargeRequest request = saveRequest("rollback-user", 0);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("test ledger failure");
        doThrow(failure).when(ledgerEntryRepository).saveAll(any());

        assertThatThrownBy(() -> chargeConfirmationManager.confirm(request, CHARGE_AMOUNT)).isSameAs(failure);

        ChargeRequestEntity savedRequest = chargeRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(savedRequest.getStatus()).isEqualTo(ChargeStatus.PENDING);
        assertThat(savedRequest.getCreditedAbcAmount()).isNull();
        assertThat(savedRequest.getVersion()).isZero();
        assertThat(ledgerTransactionRepository.count()).isZero();
        assertThat(ledgerEntryRepository.count()).isZero();
        assertThat(ledgerAccountRepository.count()).isEqualTo(1);
        LedgerAccountEntity savedSystem = ledgerAccountRepository.findById(system.getId()).orElseThrow();
        assertThat(savedSystem.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(savedSystem.getVersion()).isZero();
    }

    @Test
    void 기존_계정을_보장하는_작업은_잔액과_version을_초기화하지_않는다() {
        LedgerAccountEntity system = createSystemAccount();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ledgerAccountRepository.addBalanceDelta(system.getId(), CHARGE_AMOUNT.negate(), Instant.now());
            ledgerAccountRepository.insertIfAbsent(
                    LedgerAccountType.ABC_ISSUANCE.name(), SYSTEM_REFERENCE_ID, Instant.now()
            );
        });

        LedgerAccountEntity savedSystem = ledgerAccountRepository.findById(system.getId()).orElseThrow();
        assertThat(ledgerAccountRepository.count()).isEqualTo(1);
        assertThat(savedSystem.getBalance()).isEqualByComparingTo(CHARGE_AMOUNT.negate());
        assertThat(savedSystem.getVersion()).isEqualTo(1);
    }

    @Test
    void 직접_잔액_갱신_후_JPA_조회도_최신_잔액과_version을_반환한다() {
        LedgerAccountEntity system = createSystemAccount();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            LedgerAccountEntity cachedSystem = ledgerAccountRepository.findById(system.getId()).orElseThrow();
            assertThat(entityManager.contains(cachedSystem)).isTrue();

            ledgerAccountRepository.addBalanceDelta(system.getId(), CHARGE_AMOUNT.negate(), Instant.now());

            assertThat(entityManager.contains(cachedSystem)).isFalse();
            LedgerAccountEntity freshSystem = ledgerAccountRepository.findById(system.getId()).orElseThrow();
            assertThat(freshSystem.getBalance()).isEqualByComparingTo(CHARGE_AMOUNT.negate());
            assertThat(freshSystem.getVersion()).isEqualTo(1);
            assertThat(freshSystem.getUpdatedAt()).isNotNull();
        });
    }

    private LedgerAccountEntity createSystemAccount() {
        return ledgerAccountRepository.saveAndFlush(
                LedgerAccountEntity.of(LedgerAccountType.ABC_ISSUANCE, SYSTEM_REFERENCE_ID)
        );
    }

    private ChargeRequest saveRequest(String userReferenceId, int transactionNumber) {
        ChargeRequestEntity saved = chargeRequestRepository.saveAndFlush(ChargeRequestEntity.pending(
                userReferenceId,
                "0x1111111111111111111111111111111111111111",
                12345L,
                "0x" + String.format("%064x", transactionNumber + 1),
                CHARGE_AMOUNT
        ));
        return chargeRequestReader.read(saved.getId());
    }

    private void confirmConcurrently(List<ChargeRequest> requests) throws Exception {
        CyclicBarrier startTogether = new CyclicBarrier(requests.size());
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        List<Future<ChargeRequest>> results = new ArrayList<>();
        try {
            for (ChargeRequest request : requests) {
                results.add(executor.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    return chargeConfirmationManager.confirm(request, CHARGE_AMOUNT);
                }));
            }
            for (Future<ChargeRequest> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS).getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
            }
        } finally {
            results.forEach(result -> result.cancel(true));
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertAllCreditsPersisted(List<ChargeRequest> requests) {
        assertThat(ledgerTransactionRepository.count()).isEqualTo(requests.size());
        assertThat(ledgerEntryRepository.count()).isEqualTo(requests.size() * 2L);
        for (ChargeRequest request : requests) {
            assertThat(chargeRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                    .isEqualTo(ChargeStatus.CONFIRMED);
        }
        LedgerAccountEntity system = ledgerAccountRepository.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_ISSUANCE, SYSTEM_REFERENCE_ID).orElseThrow();
        assertThat(system.getBalance()).isEqualByComparingTo(
                CHARGE_AMOUNT.multiply(BigDecimal.valueOf(requests.size())).negate()
        );
        assertThat(system.getVersion()).isEqualTo(requests.size());
        for (LedgerAccountEntity account : ledgerAccountRepository.findAll()) {
            BigDecimal entryTotal = ledgerEntryRepository.findAllByAccountIdOrderByIdAsc(account.getId())
                    .stream().map(LedgerEntryEntity::getAmountDelta).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(account.getBalance()).isEqualByComparingTo(entryTotal);
            assertThat(account.getBalance().scale()).isEqualTo(6);
        }
    }
}
