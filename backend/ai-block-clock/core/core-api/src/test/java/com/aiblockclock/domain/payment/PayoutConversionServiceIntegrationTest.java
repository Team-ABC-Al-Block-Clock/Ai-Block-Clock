package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.identity.IdentityVerificationStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.storage.identity.IdentityVerificationEntity;
import com.aiblockclock.storage.identity.IdentityVerificationRepository;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.storage.payment.WithdrawalRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

// 잠금을 보려면 실제로 커밋되어야 해서 테스트 트랜잭션을 쓰지 않고, 따로 떼어 낸 DB 에서 돈다.
@Tag("integration")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payout_conversion;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class PayoutConversionServiceIntegrationTest {
    private static final int CONCURRENT_REQUESTS = 5;
    private static final AbcAmount TEN = AbcAmount.of(BigDecimal.TEN);

    @Autowired
    private PayoutConversionService payoutConversionService;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityVerificationRepository identityVerificationRepository;

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAllInBatch();
        ledgerTransactionRepository.deleteAllInBatch();
        ledgerAccountRepository.deleteAllInBatch();
        withdrawalRepository.deleteAllInBatch();
        identityVerificationRepository.deleteAllInBatch();
        userRepository.deleteAllByIdInBatch(createdUserIds);
    }

    @Test
    void 옮기면_정산금에서_빼_쓸_수_있는_잔액에_더하고_옮긴_뒤_잔액을_돌려준다() {
        UserId seller = sellerWithPayout("0x3000000000000000000000000000000000000001", "30");
        PayoutConversionRequestId requestId = newRequestId();

        PayoutConversion conversion = payoutConversionService.convert(seller, requestId, TEN);

        assertThat(conversion.convertedAmount()).isEqualTo(TEN);
        assertThat(conversion.balance().payout()).isEqualByComparingTo("20");
        assertThat(conversion.balance().available()).isEqualByComparingTo("10");
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("20");
        assertThat(balanceOf(seller, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
        assertThat(ledgerTransactionRepository.findAll())
                .extracting(LedgerTransactionEntity::getEventType, LedgerTransactionEntity::getIdempotencyKey)
                .containsExactly(tuple(LedgerEventType.PAYOUT_CONVERSION,
                        "payout-conversion:" + seller.value() + ":" + requestId.value()));
    }

    @Test
    void 정산금을_모두_옮길_수_있다() {
        UserId seller = sellerWithPayout("0x3000000000000000000000000000000000000002", "10");

        payoutConversionService.convert(seller, newRequestId(), TEN);

        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("0");
        assertThat(balanceOf(seller, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
    }

    @Test
    void 같은_요청_번호로_다시_보내면_또_옮기지_않고_처음_옮긴_금액을_돌려준다() {
        UserId seller = sellerWithPayout("0x3000000000000000000000000000000000000003", "30");
        PayoutConversionRequestId requestId = newRequestId();
        payoutConversionService.convert(seller, requestId, TEN);

        PayoutConversion again = payoutConversionService.convert(seller, requestId, AbcAmount.of(new BigDecimal("5")));

        assertThat(again.convertedAmount()).isEqualTo(TEN);
        assertThat(again.balance().payout()).isEqualByComparingTo("20");
        assertThat(ledgerTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    void 이미_옮겨_정산금이_줄었어도_같은_요청_번호의_재시도는_실패하지_않는다() {
        UserId seller = sellerWithPayout("0x3000000000000000000000000000000000000004", "10");
        PayoutConversionRequestId requestId = newRequestId();
        payoutConversionService.convert(seller, requestId, TEN);

        PayoutConversion again = payoutConversionService.convert(seller, requestId, TEN);

        assertThat(again.convertedAmount()).isEqualTo(TEN);
        assertThat(balanceOf(seller, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
    }

    @Test
    void 정산금보다_많이_옮기려_하면_아무것도_옮기지_않는다() {
        UserId seller = sellerWithPayout("0x3000000000000000000000000000000000000005", "3");

        assertThatThrownBy(() -> payoutConversionService.convert(seller, newRequestId(), TEN))
                .isInstanceOfSatisfying(CoreException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(ErrorType.PAYOUT_CONVERSION_INSUFFICIENT);
                    assertThat(exception.getData()).isEqualTo(Map.of("payoutAmount", "3.000000"));
                });
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("3");
        assertThat(balanceOf(seller, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("0");
        assertThat(ledgerTransactionRepository.count()).isZero();
    }

    @Test
    void 정산금을_받은_적이_없으면_옮길_수_없다() {
        UserId user = user("0x3000000000000000000000000000000000000006");

        assertThatThrownBy(() -> payoutConversionService.convert(user, newRequestId(), TEN))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.PAYOUT_CONVERSION_INSUFFICIENT));
    }

    @Test
    void 출금_신청으로_묶인_금액은_옮길_수_없다() {
        UserId seller = sellerWithPayout("0x3000000000000000000000000000000000000007", "30");
        verifyIdentity(seller);
        withdrawalService.request(seller, WithdrawalRequestId.of(UUID.randomUUID()), AbcAmount.of(new BigDecimal("25")));

        assertThatThrownBy(() -> payoutConversionService.convert(seller, newRequestId(), TEN))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.PAYOUT_CONVERSION_INSUFFICIENT));
        assertThat(balanceOf(seller, LedgerAccountType.WITHDRAWAL_ESCROW)).isEqualByComparingTo("25");
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("5");
    }

    @Test
    void 남이_같은_요청_번호를_써도_각자_옮긴다() {
        UserId seller = sellerWithPayout("0x3000000000000000000000000000000000000008", "30");
        UserId other = sellerWithPayout("0x3000000000000000000000000000000000000009", "30");
        PayoutConversionRequestId requestId = newRequestId();

        payoutConversionService.convert(seller, requestId, TEN);
        PayoutConversion othersConversion = payoutConversionService.convert(other, requestId, TEN);

        assertThat(othersConversion.balance().available()).isEqualByComparingTo("10");
        assertThat(balanceOf(seller, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
        assertThat(balanceOf(other, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
    }

    @Test
    void 서로_다른_요청_번호로_동시에_옮겨도_정산금을_넘지_않는다() throws Exception {
        UserId seller = sellerWithPayout("0x3000000000000000000000000000000000000010", "30");

        List<Object> outcomes = concurrently(Collections.nCopies(CONCURRENT_REQUESTS,
                () -> payoutConversionService.convert(seller, newRequestId(), TEN)));

        assertThat(outcomes).filteredOn(PayoutConversion.class::isInstance).hasSize(3);
        assertThat(outcomes).filteredOn(ErrorType.PAYOUT_CONVERSION_INSUFFICIENT::equals).hasSize(2);
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("0");
        assertThat(balanceOf(seller, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("30");
    }

    @Test
    void 같은_요청_번호로_동시에_보내도_한_번만_옮긴다() throws Exception {
        UserId seller = sellerWithPayout("0x3000000000000000000000000000000000000011", "30");
        PayoutConversionRequestId requestId = newRequestId();

        List<Object> outcomes = concurrently(Collections.nCopies(CONCURRENT_REQUESTS,
                () -> payoutConversionService.convert(seller, requestId, TEN)));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome).isInstanceOf(PayoutConversion.class));
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("20");
        assertThat(balanceOf(seller, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
        assertThat(ledgerTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    void 출금_신청과_동시에_와도_정산금을_넘게_쓰지_않는다() throws Exception {
        UserId seller = sellerWithPayout("0x3000000000000000000000000000000000000012", "10");
        verifyIdentity(seller);

        List<Object> outcomes = concurrently(List.of(
                () -> payoutConversionService.convert(seller, newRequestId(), TEN),
                () -> withdrawalService.request(seller, WithdrawalRequestId.of(UUID.randomUUID()), TEN)));

        assertThat(outcomes).filteredOn(outcome -> !(outcome instanceof ErrorType)).hasSize(1);
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("0");
        assertThat(balanceOf(seller, LedgerAccountType.ABC_BALANCE)
                .add(balanceOf(seller, LedgerAccountType.WITHDRAWAL_ESCROW))).isEqualByComparingTo("10");
    }

    // 모두 한꺼번에 출발시키고, 성공은 돌려받은 값으로, 실패는 오류 종류로 모은다.
    private List<Object> concurrently(List<Callable<?>> tasks) throws Exception {
        CyclicBarrier start = new CyclicBarrier(tasks.size());
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());

        try {
            List<Future<Object>> futures = new ArrayList<>();

            for (Callable<?> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);

                    try {
                        return task.call();
                    } catch (CoreException exception) {
                        return exception.getErrorType();
                    }
                }));
            }

            List<Object> outcomes = new ArrayList<>();

            for (Future<Object> future : futures) {
                try {
                    outcomes.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException exception) {
                    throw new AssertionError("동시 요청 중 예상하지 못한 예외", exception.getCause());
                }
            }

            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private UserId sellerWithPayout(String walletAddress, String payout) {
        UserId seller = user(walletAddress);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            LedgerAccountEntity account = ledgerAccountRepository.save(
                    LedgerAccountEntity.of(LedgerAccountType.PAYOUT_BALANCE, Long.toString(seller.value())));
            ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(payout), Instant.now());
        });

        return seller;
    }

    private UserId user(String walletAddress) {
        Long id = userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
        createdUserIds.add(id);
        return UserId.of(id);
    }

    // 옮기기는 본인 확인이 필요 없다. 출금과 겹치는 경우를 볼 때만 승인받은 사용자로 만든다.
    private void verifyIdentity(UserId user) {
        identityVerificationRepository.saveAndFlush(IdentityVerificationEntity.of(
                user.value(), "판매자", LocalDate.of(1990, 1, 1), IdentityVerificationStatus.APPROVED, Instant.now()));
    }

    private BigDecimal balanceOf(UserId owner, LedgerAccountType accountType) {
        return ledgerAccountRepository.findByAccountTypeAndReferenceId(accountType, Long.toString(owner.value()))
                .map(LedgerAccountEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    private PayoutConversionRequestId newRequestId() {
        return PayoutConversionRequestId.of(UUID.randomUUID());
    }
}
