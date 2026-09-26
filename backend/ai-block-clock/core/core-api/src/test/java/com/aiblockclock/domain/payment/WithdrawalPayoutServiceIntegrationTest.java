package com.aiblockclock.domain.payment;

import com.aiblockclock.client.blockchain.BlockchainClientError;
import com.aiblockclock.client.blockchain.BlockchainClientException;
import com.aiblockclock.client.blockchain.ChainTransaction;
import com.aiblockclock.client.blockchain.ChainTransactionStatus;
import com.aiblockclock.client.blockchain.SsafyChainClient;
import com.aiblockclock.client.blockchain.SsafyStoClient;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.payment.WithdrawalPayoutResult.Outcome;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.crypto.Hash;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SignatureException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 체인 · 토큰증권 API 는 가짜로 두고, 서명은 공개 테스트 키로 실제로 한다. 보관 지갑 주소를 그 키의 주소로 맞춘다.
// 잠금과 조건부 UPDATE 를 보려면 실제로 커밋되어야 해서 테스트 트랜잭션을 쓰지 않고, 따로 떼어 낸 DB 에서 돈다.
// 키와 보관 지갑은 MySQL 판(@PaymentMySqlTest 가 @SpringBootTest 를 바꾼다)도 물려받도록 @TestPropertySource 에 둔다.
@Tag("integration")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:withdrawal_payout;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@TestPropertySource(properties = {
        "ssafy.chain.signer.private-key=" + WithdrawalPayoutServiceIntegrationTest.TEST_PRIVATE_KEY,
        "payment.charge.custody-address=" + WithdrawalPayoutServiceIntegrationTest.CUSTODY,
        "payment.charge.chain-id=31221",
        "payment.charge.deposit-token-contract-address=" + WithdrawalPayoutServiceIntegrationTest.TOKEN
})
class WithdrawalPayoutServiceIntegrationTest {
    // Hardhat 이 모든 개발자에게 똑같이 주는 공개 테스트 계정이다. 어느 체인에도 자산이 없다
    static final String TEST_PRIVATE_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    static final String CUSTODY = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
    static final String TOKEN = "0x56DC097C14836473f4Ad4BD675a3d01BE5e4C63c";
    private static final long CHAIN_ID = 31221L;
    private static final BigInteger PLENTY = BigInteger.TEN.pow(30);
    private static final int CONCURRENT_RUNS = 5;

    @Autowired
    private WithdrawalPayoutService withdrawalPayoutService;

    @Autowired
    private WithdrawalService withdrawalService;

    @MockitoBean
    private SsafyChainClient ssafyChainClient;

    @MockitoBean
    private SsafyStoClient ssafyStoClient;

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Autowired
    private IdentityVerificationRepository identityVerificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    void 체인은_기본으로_받아_주고_아직_블록에_적지_않는다() {
        given(ssafyStoClient.getBalance(anyString(), anyString())).willReturn(PLENTY);
        given(ssafyChainClient.readPendingNonce(anyString())).willReturn(BigInteger.ZERO);
        given(ssafyChainClient.estimateTokenTransferGas(anyString(), anyString(), anyString(), any()))
                .willReturn(BigInteger.valueOf(60_000));
        given(ssafyChainClient.readTransaction(anyString())).willReturn(chain(ChainTransactionStatus.PENDING));
    }

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
    void 승인된_출금을_받을_지갑으로_서명해_보내고_해시를_먼저_적는다() throws SignatureException {
        Withdrawal approved = approvedWithdrawal("0x5000000000000000000000000000000000000001", "12.5");

        WithdrawalPayoutResult result = withdrawalPayoutService.payNext();

        Withdrawal paying = reload(approved);
        String raw = submittedRawTransactions().get(0);
        SignedRawTransaction decoded = (SignedRawTransaction) TransactionDecoder.decode(raw);

        assertThat(result).isEqualTo(new WithdrawalPayoutResult(Outcome.SUBMITTED, approved.getId()));
        assertThat(paying.getStatus()).isEqualTo(WithdrawalStatus.PAYING);
        assertThat(paying.getPayoutTransaction().rawTransaction()).isEqualTo(raw);
        assertThat(paying.getPayoutTransaction().transactionHash())
                .isEqualTo(Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(raw))));
        assertThat(paying.getPayoutSubmittedAt()).isNotNull();
        assertThat(decoded.getFrom()).isEqualToIgnoringCase(CUSTODY);
        assertThat(decoded.getTo()).isEqualToIgnoringCase(TOKEN);
        assertThat(decoded.getChainId()).isEqualTo(CHAIN_ID);
        assertThat(decoded.getData()).contains("5000000000000000000000000000000000000001")
                .endsWith(String.format("%064x", new BigInteger("12500000000000000000")));
        assertThat(balanceOf(approved.getRequester(), LedgerAccountType.WITHDRAWAL_ESCROW)).isEqualByComparingTo("12.5");
        verify(ssafyChainClient).estimateTokenTransferGas(CUSTODY, TOKEN,
                "0x5000000000000000000000000000000000000001", new BigInteger("12500000000000000000"));
    }

    @Test
    void 체인에서_성공하면_지급을_끝내고_묶은_금액을_장부에서_내보낸다() {
        Withdrawal approved = approvedWithdrawal("0x5000000000000000000000000000000000000002", "10");
        withdrawalPayoutService.payNext();
        String hash = reload(approved).getPayoutTransaction().transactionHash();
        given(ssafyChainClient.readTransaction(hash)).willReturn(chain(ChainTransactionStatus.SUCCEEDED));

        WithdrawalPayoutResult result = withdrawalPayoutService.payNext();

        Withdrawal paid = reload(approved);

        assertThat(result).isEqualTo(new WithdrawalPayoutResult(Outcome.IDLE, null));
        assertThat(paid.getStatus()).isEqualTo(WithdrawalStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
        assertThat(balanceOf(approved.getRequester(), LedgerAccountType.WITHDRAWAL_ESCROW)).isEqualByComparingTo("0");
        assertThat(ledgerAccountRepository.findByAccountTypeAndReferenceId(LedgerAccountType.ABC_ISSUANCE, "SYSTEM")
                .map(LedgerAccountEntity::getBalance).orElseThrow()).isEqualByComparingTo("10");
        assertThat(ledgerTransactionRepository.findAll())
                .filteredOn(transaction -> transaction.getEventType() == LedgerEventType.WITHDRAWAL_PAYOUT)
                .extracting(LedgerTransactionEntity::getIdempotencyKey, LedgerTransactionEntity::getChainId,
                        LedgerTransactionEntity::getTransactionHash)
                .containsExactly(tuple("withdrawal-payout:" + approved.getId(), CHAIN_ID, hash));
    }

    @Test
    void 끝낸_출금을_다시_봐도_장부에는_한_번만_적는다() {
        Withdrawal approved = approvedWithdrawal("0x5000000000000000000000000000000000000003", "10");
        withdrawalPayoutService.payNext();
        given(ssafyChainClient.readTransaction(anyString())).willReturn(chain(ChainTransactionStatus.SUCCEEDED));
        withdrawalPayoutService.payNext();

        WithdrawalPayoutResult again = withdrawalPayoutService.payNext();

        assertThat(again.outcome()).isEqualTo(Outcome.IDLE);
        assertThat(ledgerTransactionRepository.findAll())
                .filteredOn(transaction -> transaction.getEventType() == LedgerEventType.WITHDRAWAL_PAYOUT)
                .hasSize(1);
        assertThat(reload(approved).isPaid()).isTrue();
    }

    @Test
    void 블록에_적히기_전이면_기다리고_다시_보내지_않는다() {
        Withdrawal approved = approvedWithdrawal("0x5000000000000000000000000000000000000004", "10");
        withdrawalPayoutService.payNext();

        WithdrawalPayoutResult result = withdrawalPayoutService.payNext();

        assertThat(result).isEqualTo(new WithdrawalPayoutResult(Outcome.WAITING, approved.getId()));
        assertThat(reload(approved).getStatus()).isEqualTo(WithdrawalStatus.PAYING);
        verify(ssafyChainClient, times(1)).submitRawTransaction(anyString());
    }

    @Test
    void 노드가_거래를_모르면_새로_서명하지_않고_적어_둔_거래를_그대로_다시_보낸다() {
        Withdrawal approved = approvedWithdrawal("0x5000000000000000000000000000000000000005", "10");
        withdrawalPayoutService.payNext();
        given(ssafyChainClient.readTransaction(anyString())).willReturn(chain(ChainTransactionStatus.NOT_FOUND));

        WithdrawalPayoutResult result = withdrawalPayoutService.payNext();

        List<String> submitted = submittedRawTransactions();

        assertThat(result.outcome()).isEqualTo(Outcome.WAITING);
        assertThat(submitted).hasSize(2).containsOnly(reload(approved).getPayoutTransaction().rawTransaction());
        verify(ssafyChainClient, times(1)).readPendingNonce(anyString());
    }

    @Test
    void 보내다_끊겨도_지급_중으로_남고_다음_차례에_같은_거래를_다시_보낸다() {
        Withdrawal approved = approvedWithdrawal("0x5000000000000000000000000000000000000006", "10");
        willThrow(new BlockchainClientException(BlockchainClientError.REQUEST_FAILED, "timeout"))
                .given(ssafyChainClient).submitRawTransaction(anyString());

        WithdrawalPayoutResult first = withdrawalPayoutService.payNext();

        assertThat(first).isEqualTo(new WithdrawalPayoutResult(Outcome.UNREACHABLE, approved.getId()));
        assertThat(reload(approved).getStatus()).isEqualTo(WithdrawalStatus.PAYING);

        given(ssafyChainClient.readTransaction(anyString())).willReturn(chain(ChainTransactionStatus.NOT_FOUND));
        willDoNothing().given(ssafyChainClient).submitRawTransaction(anyString());

        withdrawalPayoutService.payNext();

        assertThat(submittedRawTransactions()).hasSize(2).containsOnly(
                reload(approved).getPayoutTransaction().rawTransaction());
        verify(ssafyChainClient, times(1)).readPendingNonce(anyString());
    }

    @Test
    void 보관_지갑_잔액이_모자라면_서명하지_않고_승인_상태로_둔다() {
        Withdrawal approved = approvedWithdrawal("0x5000000000000000000000000000000000000007", "10");
        given(ssafyStoClient.getBalance(anyString(), anyString())).willReturn(BigInteger.TEN.pow(19).subtract(BigInteger.ONE));

        WithdrawalPayoutResult result = withdrawalPayoutService.payNext();

        assertThat(result).isEqualTo(new WithdrawalPayoutResult(Outcome.CUSTODY_BALANCE_SHORT, approved.getId()));
        assertThat(reload(approved).getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
        verify(ssafyChainClient, never()).submitRawTransaction(anyString());
    }

    @Test
    void 체인에_닿지_못하면_서명하지_않고_승인_상태로_둔다() {
        Withdrawal approved = approvedWithdrawal("0x5000000000000000000000000000000000000008", "10");
        given(ssafyChainClient.readPendingNonce(anyString()))
                .willThrow(new BlockchainClientException(BlockchainClientError.REQUEST_FAILED, "timeout"));

        WithdrawalPayoutResult result = withdrawalPayoutService.payNext();

        assertThat(result).isEqualTo(new WithdrawalPayoutResult(Outcome.UNREACHABLE, approved.getId()));
        assertThat(reload(approved).getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
        assertThat(reload(approved).getPayoutTransaction()).isNull();
    }

    @Test
    void 체인이_거래를_되돌리면_지급_중으로_남기고_뒤의_출금도_보내지_않는다() {
        Withdrawal first = approvedWithdrawal("0x5000000000000000000000000000000000000009", "10");
        Withdrawal second = approvedWithdrawal("0x5000000000000000000000000000000000000010", "10");
        withdrawalPayoutService.payNext();
        given(ssafyChainClient.readTransaction(anyString())).willReturn(chain(ChainTransactionStatus.FAILED));

        WithdrawalPayoutResult result = withdrawalPayoutService.payNext();

        assertThat(result).isEqualTo(new WithdrawalPayoutResult(Outcome.FAILED_ON_CHAIN, first.getId()));
        assertThat(reload(first).getStatus()).isEqualTo(WithdrawalStatus.PAYING);
        assertThat(reload(second).getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
        assertThat(balanceOf(first.getRequester(), LedgerAccountType.WITHDRAWAL_ESCROW)).isEqualByComparingTo("10");
        verify(ssafyChainClient, times(1)).submitRawTransaction(anyString());
    }

    @Test
    void 승인이_오래된_것부터_한_건씩_보내고_앞의_것이_끝나면_곧바로_다음_것을_보낸다() {
        Withdrawal first = approvedWithdrawal("0x5000000000000000000000000000000000000011", "10");
        Withdrawal second = approvedWithdrawal("0x5000000000000000000000000000000000000012", "10");

        withdrawalPayoutService.payNext();

        assertThat(reload(first).getStatus()).isEqualTo(WithdrawalStatus.PAYING);
        assertThat(reload(second).getStatus()).isEqualTo(WithdrawalStatus.APPROVED);

        given(ssafyChainClient.readTransaction(reload(first).getPayoutTransaction().transactionHash()))
                .willReturn(chain(ChainTransactionStatus.SUCCEEDED));
        given(ssafyChainClient.readPendingNonce(anyString())).willReturn(BigInteger.ONE);

        WithdrawalPayoutResult result = withdrawalPayoutService.payNext();

        assertThat(result).isEqualTo(new WithdrawalPayoutResult(Outcome.SUBMITTED, second.getId()));
        assertThat(reload(first).isPaid()).isTrue();
        assertThat(reload(second).getStatus()).isEqualTo(WithdrawalStatus.PAYING);
    }

    @Test
    void 심사를_기다리는_출금은_보내지_않는다() {
        UserId seller = sellerWithPayout("0x5000000000000000000000000000000000000013", "30");
        withdrawalService.request(seller, WithdrawalRequestId.of(UUID.randomUUID()), AbcAmount.of(BigDecimal.TEN));

        WithdrawalPayoutResult result = withdrawalPayoutService.payNext();

        assertThat(result.outcome()).isEqualTo(Outcome.IDLE);
        verify(ssafyChainClient, never()).submitRawTransaction(anyString());
    }

    @Test
    void 지급_차례가_동시에_돌아도_한_출금에_거래는_하나만_보낸다() throws Exception {
        Withdrawal approved = approvedWithdrawal("0x5000000000000000000000000000000000000014", "10");

        List<Object> outcomes = concurrently(() -> withdrawalPayoutService.payNext().outcome());

        assertThat(outcomes).filteredOn(Outcome.SUBMITTED::equals).hasSize(1);
        assertThat(outcomes).containsOnly(Outcome.SUBMITTED, Outcome.WAITING);
        assertThat(reload(approved).getStatus()).isEqualTo(WithdrawalStatus.PAYING);
        verify(ssafyChainClient, times(1)).submitRawTransaction(anyString());
    }

    @Test
    void 서명할_지갑이_보관_지갑이면_지급을_켤_수_있다() {
        withdrawalPayoutService.ensureReady();
    }

    private List<String> submittedRawTransactions() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ssafyChainClient, atLeastOnce()).submitRawTransaction(captor.capture());
        return captor.getAllValues();
    }

    // 모두 한꺼번에 출발시키고, 성공은 돌려받은 값으로, 실패는 오류 종류로 모은다.
    private List<Object> concurrently(Callable<Object> task) throws Exception {
        CyclicBarrier start = new CyclicBarrier(CONCURRENT_RUNS);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_RUNS);

        try {
            List<Future<Object>> futures = new ArrayList<>();

            for (int i = 0; i < CONCURRENT_RUNS; i++) {
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
                    throw new AssertionError("동시 지급 중 예상하지 못한 예외", exception.getCause());
                }
            }

            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private Withdrawal approvedWithdrawal(String walletAddress, String amount) {
        UserId seller = sellerWithPayout(walletAddress, amount);
        Withdrawal requested = withdrawalService.request(
                seller, WithdrawalRequestId.of(UUID.randomUUID()), AbcAmount.of(new BigDecimal(amount)));

        return withdrawalService.approve(UserId.of(999_999L), requested.getId());
    }

    private UserId sellerWithPayout(String walletAddress, String payout) {
        Long id = userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
        createdUserIds.add(id);
        identityVerificationRepository.saveAndFlush(IdentityVerificationEntity.of(
                id, "판매자", LocalDate.of(1990, 1, 1), IdentityVerificationStatus.APPROVED, Instant.now()));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            LedgerAccountEntity account = ledgerAccountRepository.save(
                    LedgerAccountEntity.of(LedgerAccountType.PAYOUT_BALANCE, Long.toString(id)));
            ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(payout), Instant.now());
        });

        return UserId.of(id);
    }

    private Withdrawal reload(Withdrawal withdrawal) {
        return withdrawalRepository.findById(withdrawal.getId())
                .map(WithdrawalEntityConverter::toDomain)
                .orElseThrow();
    }

    private BigDecimal balanceOf(UserId owner, LedgerAccountType accountType) {
        return ledgerAccountRepository.findByAccountTypeAndReferenceId(accountType, Long.toString(owner.value()))
                .map(LedgerAccountEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    private static ChainTransaction chain(ChainTransactionStatus status) {
        return new ChainTransaction(status, CHAIN_ID, null, null, List.of());
    }
}
