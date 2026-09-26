package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.identity.IdentityVerificationStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.enums.payment.WithdrawalStatus;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

// 잠금과 되돌림을 보려면 실제로 커밋 · 롤백되어야 해서 테스트 트랜잭션을 쓰지 않고, 따로 떼어 낸 DB 에서 돈다.
@Tag("integration")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:withdrawal_service;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class WithdrawalServiceIntegrationTest {
    private static final int CONCURRENT_REQUESTS = 5;
    private static final AbcAmount TEN = AbcAmount.of(BigDecimal.TEN);
    private static final UserId ADMIN = UserId.of(999_999L);

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private IdentityVerificationRepository identityVerificationRepository;

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @MockitoSpyBean
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
    void 신청하면_정산금을_출금_예치로_묶고_로그인_지갑을_받을_곳으로_적는다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000001", "30");

        Withdrawal withdrawal = withdrawalService.request(seller, newRequestId(), TEN);

        assertThat(withdrawal.getId()).isNotNull();
        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.REQUESTED);
        assertThat(withdrawal.getAmount()).isEqualTo(TEN);
        assertThat(withdrawal.getWalletAddress()).isEqualTo("0x1000000000000000000000000000000000000001");
        assertThat(withdrawal.getRequestedAt()).isNotNull();
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("20");
        assertThat(balanceOf(seller, LedgerAccountType.WITHDRAWAL_ESCROW)).isEqualByComparingTo("10");
        assertThat(ledgerTransactionRepository.findAll())
                .extracting(LedgerTransactionEntity::getEventType, LedgerTransactionEntity::getIdempotencyKey)
                .containsExactly(tuple(
                        LedgerEventType.WITHDRAWAL_HOLD, "withdrawal-hold:" + withdrawal.getId()));
    }

    @Test
    void 같은_요청_번호로_다시_신청하면_앞의_신청을_돌려주고_한_번만_묶는다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000002", "30");
        WithdrawalRequestId requestId = newRequestId();

        Withdrawal first = withdrawalService.request(seller, requestId, TEN);
        Withdrawal again = withdrawalService.request(seller, requestId, TEN);

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(withdrawalRepository.count()).isEqualTo(1);
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("20");
    }

    @Test
    void 처리_중인_출금이_있으면_새로_신청할_수_없다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000003", "30");
        withdrawalService.request(seller, newRequestId(), TEN);

        assertThatThrownBy(() -> withdrawalService.request(seller, newRequestId(), TEN))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_ALREADY_IN_PROGRESS));
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("20");
    }

    @Test
    void 정산금보다_많이_신청하면_신청도_묶기도_남지_않는다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000004", "9.5");

        assertThatThrownBy(() -> withdrawalService.request(seller, newRequestId(), TEN))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_PAYOUT_INSUFFICIENT));
        assertThat(withdrawalRepository.count()).isZero();
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("9.5");
    }

    @Test
    void 정산금을_받은_적이_없으면_신청할_수_없다() {
        UserId newcomer = user("0x1000000000000000000000000000000000000005");

        assertThatThrownBy(() -> withdrawalService.request(newcomer, newRequestId(), TEN))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_PAYOUT_INSUFFICIENT));
    }

    @Test
    void 장부에_적지_못하면_신청도_함께_되돌려진다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000006", "30");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("test ledger failure");
        doThrow(failure).when(ledgerEntryRepository).saveAll(any());

        assertThatThrownBy(() -> withdrawalService.request(seller, newRequestId(), TEN)).isSameAs(failure);

        assertThat(withdrawalRepository.count()).isZero();
        assertThat(ledgerTransactionRepository.count()).isZero();
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("30");
    }

    @Test
    void 취소하면_묶은_금액이_정산금으로_돌아오고_곧바로_다시_신청할_수_있다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000007", "30");
        Withdrawal requested = withdrawalService.request(seller, newRequestId(), TEN);

        Withdrawal canceled = withdrawalService.cancel(seller, requested.getId());

        assertThat(canceled.getStatus()).isEqualTo(WithdrawalStatus.CANCELED);
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("30");
        assertThat(balanceOf(seller, LedgerAccountType.WITHDRAWAL_ESCROW)).isEqualByComparingTo("0");

        Withdrawal next = withdrawalService.request(seller, newRequestId(), AbcAmount.of(new BigDecimal("30")));

        assertThat(next.getStatus()).isEqualTo(WithdrawalStatus.REQUESTED);
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("0");
    }

    @Test
    void 이미_취소한_신청을_다시_취소해도_한_번만_돌려준다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000008", "30");
        Withdrawal requested = withdrawalService.request(seller, newRequestId(), TEN);
        withdrawalService.cancel(seller, requested.getId());

        Withdrawal again = withdrawalService.cancel(seller, requested.getId());

        assertThat(again.getStatus()).isEqualTo(WithdrawalStatus.CANCELED);
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("30");
        assertThat(ledgerTransactionRepository.findAll())
                .extracting(LedgerTransactionEntity::getEventType)
                .containsExactlyInAnyOrder(LedgerEventType.WITHDRAWAL_HOLD, LedgerEventType.WITHDRAWAL_RELEASE);
    }

    @Test
    void 남의_신청은_없는_것처럼_취소할_수_없다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000009", "30");
        UserId other = user("0x100000000000000000000000000000000000000a");
        Withdrawal requested = withdrawalService.request(seller, newRequestId(), TEN);

        assertThatThrownBy(() -> withdrawalService.cancel(other, requested.getId()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_NOT_FOUND));
        assertThat(balanceOf(seller, LedgerAccountType.WITHDRAWAL_ESCROW)).isEqualByComparingTo("10");
    }

    @Test
    void 서로_다른_요청_번호로_동시에_신청해도_하나만_묶인다() throws Exception {
        UserId seller = sellerWithPayout("0x100000000000000000000000000000000000000b", "100");

        List<Object> outcomes = concurrently(() -> withdrawalService.request(seller, newRequestId(), TEN));

        assertThat(outcomes).filteredOn(Withdrawal.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ErrorType.class::isInstance)
                .containsOnly(ErrorType.WITHDRAWAL_ALREADY_IN_PROGRESS)
                .hasSize(CONCURRENT_REQUESTS - 1);
        assertThat(withdrawalRepository.count()).isEqualTo(1);
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("90");
        assertThat(balanceOf(seller, LedgerAccountType.WITHDRAWAL_ESCROW)).isEqualByComparingTo("10");
    }

    @Test
    void 같은_요청_번호로_동시에_신청해도_한_번만_묶는다() throws Exception {
        UserId seller = sellerWithPayout("0x100000000000000000000000000000000000000c", "100");
        WithdrawalRequestId requestId = newRequestId();

        List<Object> outcomes = concurrently(() -> withdrawalService.request(seller, requestId, TEN));

        assertThat(outcomes).filteredOn(Withdrawal.class::isInstance)
                .extracting(outcome -> ((Withdrawal) outcome).getId())
                .containsOnly(withdrawalRepository.findAll().get(0).getId());
        assertThat(withdrawalRepository.count()).isEqualTo(1);
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("90");
    }

    @Test
    void 본인_확인을_승인받지_않았으면_신청할_수_없고_아무것도_묶지_않는다() {
        UserId seller = unverifiedUser("0x100000000000000000000000000000000000000d");
        givePayout(seller, "30");

        assertThatThrownBy(() -> withdrawalService.request(seller, newRequestId(), TEN))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_IDENTITY_NOT_VERIFIED));
        assertThat(withdrawalRepository.count()).isZero();
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("30");
    }

    @Test
    void 본인_확인이_반려된_상태면_신청할_수_없다() {
        UserId seller = unverifiedUser("0x100000000000000000000000000000000000000e");
        identity(seller, IdentityVerificationStatus.REJECTED);
        givePayout(seller, "30");

        assertThatThrownBy(() -> withdrawalService.request(seller, newRequestId(), TEN))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_IDENTITY_NOT_VERIFIED));
    }

    @Test
    void 승인하면_지급_대기가_되고_금액은_묶인_채로_남으며_공급자는_취소할_수_없다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000011", "30");
        Withdrawal requested = withdrawalService.request(seller, newRequestId(), TEN);

        Withdrawal approved = withdrawalService.approve(ADMIN, requested.getId());

        assertThat(approved.getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
        assertThat(approved.getReviewer()).isEqualTo(ADMIN);
        assertThat(approved.getReviewedAt()).isNotNull();
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("20");
        assertThat(balanceOf(seller, LedgerAccountType.WITHDRAWAL_ESCROW)).isEqualByComparingTo("10");
        assertThatThrownBy(() -> withdrawalService.cancel(seller, requested.getId()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_INVALID_STATE));
    }

    @Test
    void 승인된_신청은_처리_중이라_새로_신청할_수_없다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000012", "30");
        Withdrawal requested = withdrawalService.request(seller, newRequestId(), TEN);
        withdrawalService.approve(ADMIN, requested.getId());

        assertThatThrownBy(() -> withdrawalService.request(seller, newRequestId(), TEN))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_ALREADY_IN_PROGRESS));
    }

    @Test
    void 이미_승인한_신청을_다시_승인하면_그대로_돌려준다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000013", "30");
        Withdrawal requested = withdrawalService.request(seller, newRequestId(), TEN);
        Withdrawal approved = withdrawalService.approve(ADMIN, requested.getId());

        Withdrawal again = withdrawalService.approve(UserId.of(888_888L), requested.getId());

        assertThat(again.getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
        assertThat(again.getReviewer()).isEqualTo(approved.getReviewer());
    }

    @Test
    void 반려하면_사유를_남기고_묶은_금액을_돌려놓아_곧바로_다시_신청할_수_있다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000014", "30");
        Withdrawal requested = withdrawalService.request(seller, newRequestId(), TEN);

        Withdrawal rejected = withdrawalService.reject(ADMIN, requested.getId(), "정산 내역 확인이 필요합니다");

        assertThat(rejected.getStatus()).isEqualTo(WithdrawalStatus.REJECTED);
        assertThat(rejected.getRejectReason()).isEqualTo("정산 내역 확인이 필요합니다");
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("30");
        assertThat(balanceOf(seller, LedgerAccountType.WITHDRAWAL_ESCROW)).isEqualByComparingTo("0");
        assertThat(ledgerTransactionRepository.findAll())
                .extracting(LedgerTransactionEntity::getEventType, LedgerTransactionEntity::getIdempotencyKey)
                .contains(tuple(LedgerEventType.WITHDRAWAL_RELEASE, "withdrawal-release:" + requested.getId()));

        Withdrawal next = withdrawalService.request(seller, newRequestId(), AbcAmount.of(new BigDecimal("30")));

        assertThat(next.getStatus()).isEqualTo(WithdrawalStatus.REQUESTED);
    }

    @Test
    void 이미_반려한_신청을_다시_반려해도_한_번만_돌려놓는다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000015", "30");
        Withdrawal requested = withdrawalService.request(seller, newRequestId(), TEN);
        withdrawalService.reject(ADMIN, requested.getId(), "첫 사유");

        Withdrawal again = withdrawalService.reject(ADMIN, requested.getId(), "두 번째 사유");

        assertThat(again.getRejectReason()).isEqualTo("첫 사유");
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("30");
        assertThat(ledgerTransactionRepository.findAll())
                .extracting(LedgerTransactionEntity::getEventType)
                .containsExactlyInAnyOrder(LedgerEventType.WITHDRAWAL_HOLD, LedgerEventType.WITHDRAWAL_RELEASE);
    }

    @Test
    void 취소한_신청은_승인도_반려도_할_수_없다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000016", "30");
        Withdrawal requested = withdrawalService.request(seller, newRequestId(), TEN);
        withdrawalService.cancel(seller, requested.getId());

        assertThatThrownBy(() -> withdrawalService.approve(ADMIN, requested.getId()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_INVALID_STATE));
        assertThatThrownBy(() -> withdrawalService.reject(ADMIN, requested.getId(), "사유"))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_INVALID_STATE));
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("30");
    }

    @Test
    void 반려에서_장부에_적지_못하면_반려도_함께_되돌려진다() {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000017", "30");
        Withdrawal requested = withdrawalService.request(seller, newRequestId(), TEN);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("test ledger failure");
        doThrow(failure).when(ledgerEntryRepository).saveAll(any());

        assertThatThrownBy(() -> withdrawalService.reject(ADMIN, requested.getId(), "사유")).isSameAs(failure);

        assertThat(withdrawalRepository.findById(requested.getId()).orElseThrow().getStatus())
                .isEqualTo(WithdrawalStatus.REQUESTED);
        assertThat(balanceOf(seller, LedgerAccountType.WITHDRAWAL_ESCROW)).isEqualByComparingTo("10");
    }

    @Test
    void 공급자_취소와_관리자_반려가_동시에_와도_한쪽만_되고_한_번만_돌려놓는다() throws Exception {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000018", "30");
        Withdrawal requested = withdrawalService.request(seller, newRequestId(), TEN);

        List<Object> outcomes = concurrently(List.of(
                () -> withdrawalService.cancel(seller, requested.getId()),
                () -> withdrawalService.reject(ADMIN, requested.getId(), "사유")));

        assertThat(outcomes).filteredOn(Withdrawal.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ErrorType.class::isInstance)
                .containsExactly(ErrorType.WITHDRAWAL_INVALID_STATE);
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("30");
        assertThat(balanceOf(seller, LedgerAccountType.WITHDRAWAL_ESCROW)).isEqualByComparingTo("0");
        assertThat(ledgerTransactionRepository.findAll())
                .filteredOn(transaction -> transaction.getEventType() == LedgerEventType.WITHDRAWAL_RELEASE)
                .hasSize(1);
    }

    @Test
    void 관리자_여럿이_동시에_반려해도_한_번만_돌려놓는다() throws Exception {
        UserId seller = sellerWithPayout("0x1000000000000000000000000000000000000019", "30");
        Withdrawal requested = withdrawalService.request(seller, newRequestId(), TEN);

        List<Object> outcomes = concurrently(() -> withdrawalService.reject(ADMIN, requested.getId(), "사유"));

        assertThat(outcomes).filteredOn(Withdrawal.class::isInstance).isNotEmpty();
        assertThat(outcomes).filteredOn(ErrorType.class::isInstance)
                .containsOnly(ErrorType.WITHDRAWAL_INVALID_STATE);
        assertThat(balanceOf(seller, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("30");
        assertThat(ledgerTransactionRepository.findAll())
                .filteredOn(transaction -> transaction.getEventType() == LedgerEventType.WITHDRAWAL_RELEASE)
                .hasSize(1);
    }

    @Test
    void 심사_대기_목록은_오래된_신청부터_본인_확인과_함께_준다() {
        UserId first = sellerWithPayout("0x100000000000000000000000000000000000001a", "30");
        UserId second = sellerWithPayout("0x100000000000000000000000000000000000001b", "30");
        Withdrawal older = withdrawalService.request(first, newRequestId(), TEN);
        Withdrawal newer = withdrawalService.request(second, newRequestId(), TEN);
        UserId third = sellerWithPayout("0x100000000000000000000000000000000000001c", "30");
        Withdrawal approved = withdrawalService.request(third, newRequestId(), TEN);
        withdrawalService.approve(ADMIN, approved.getId());

        List<PendingWithdrawal> pending = withdrawalService.readAllPending();

        assertThat(pending).extracting(item -> item.withdrawal().getId())
                .containsExactly(older.getId(), newer.getId());
        assertThat(pending).extracting(item -> item.identity().getUserId())
                .containsExactly(first, second);
    }

    private List<Object> concurrently(Callable<Withdrawal> task) throws Exception {
        return concurrently(Collections.nCopies(CONCURRENT_REQUESTS, task));
    }

    // 모두 한꺼번에 출발시키고, 성공은 돌려받은 신청으로, 실패는 오류 종류로 모은다.
    private List<Object> concurrently(List<Callable<Withdrawal>> tasks) throws Exception {
        CyclicBarrier start = new CyclicBarrier(tasks.size());
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());

        try {
            List<Future<Object>> futures = new ArrayList<>();

            for (Callable<Withdrawal> task : tasks) {
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
                    throw new AssertionError("동시 신청 중 예상하지 못한 예외", exception.getCause());
                }
            }

            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private UserId sellerWithPayout(String walletAddress, String payout) {
        UserId seller = user(walletAddress);
        givePayout(seller, payout);

        return seller;
    }

    private void givePayout(UserId seller, String payout) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            LedgerAccountEntity account = ledgerAccountRepository.save(
                    LedgerAccountEntity.of(LedgerAccountType.PAYOUT_BALANCE, Long.toString(seller.value())));
            ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(payout), Instant.now());
        });
    }

    // 출금은 본인 확인을 승인받아야 신청할 수 있어, 따로 말하지 않으면 승인받은 사용자로 만든다.
    private UserId user(String walletAddress) {
        UserId user = unverifiedUser(walletAddress);
        identity(user, IdentityVerificationStatus.APPROVED);

        return user;
    }

    private UserId unverifiedUser(String walletAddress) {
        Long id = userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
        createdUserIds.add(id);
        return UserId.of(id);
    }

    private void identity(UserId user, IdentityVerificationStatus status) {
        identityVerificationRepository.saveAndFlush(IdentityVerificationEntity.of(
                user.value(), "판매자", LocalDate.of(1990, 1, 1), status, Instant.now()));
    }

    private BigDecimal balanceOf(UserId owner, LedgerAccountType accountType) {
        return ledgerAccountRepository.findByAccountTypeAndReferenceId(accountType, Long.toString(owner.value()))
                .map(LedgerAccountEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    private WithdrawalRequestId newRequestId() {
        return WithdrawalRequestId.of(UUID.randomUUID());
    }
}
