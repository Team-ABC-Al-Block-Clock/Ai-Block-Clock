package com.aiblockclock.domain.payment;

import com.aiblockclock.client.blockchain.SsafyChainClient;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@PaymentMySqlTest
class ChargeAmountMySqlTest {

    private static final String HASH = "0x" + "a".repeat(64);
    private static final String WALLET = "0x" + "1".repeat(40);

    @Autowired
    private ChargeRequestService requestService;
    @Autowired
    private ChargeProcessingService processingService;
    @Autowired
    private ChargeRequestRepository requests;
    @Autowired
    private LedgerTransactionRepository transactions;
    @Autowired
    private LedgerEntryRepository entries;
    @Autowired
    private LedgerAccountRepository accounts;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository users;

    @MockitoBean
    private SsafyChainClient ssafyChainClient;

    @BeforeEach
    void 실제_MySQL과_RR_및_InnoDB_환경을_확인한다() {
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        assertThat(jdbc.queryForObject("SELECT @@session.transaction_isolation", String.class))
                .isEqualTo("REPEATABLE-READ");
        assertThat(jdbc.queryForList("SELECT ENGINE FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN "
                + "('charge_request', 'ledger_account', 'ledger_transaction', 'ledger_entry')", String.class))
                .hasSize(4).containsOnly("InnoDB");
    }

    @AfterEach
    void cleanUp() {
        entries.deleteAllInBatch();
        transactions.deleteAllInBatch();
        accounts.deleteAllInBatch();
        requests.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.000001", "1.000001", "10.123456"})
    void 소수_6자리_충전이_요청과_원장과_잔액에_정확히_커밋된다(String amount) {
        BigDecimal expected = new BigDecimal(amount);
        UserEntity user = users.saveAndFlush(UserEntity.of(WALLET));
        given(ssafyChainClient.readTransaction(HASH)).willReturn(ChargeChainFixtures.deposited(WALLET, expected));
        ChargeRequest request = requestService.createChargeRequest(UserId.of(user.getId()), HASH, expected);

        assertThat(processingService.process(request.getId()).chargeRequest().getStatus())
                .isEqualTo(ChargeStatus.CONFIRMED);
        var savedRequest = requests.findById(request.getId()).orElseThrow();
        assertAmount(savedRequest.getRequestedDepositAmount(), expected);
        assertAmount(savedRequest.getCreditedAbcAmount(), expected);
        var transaction = transactions.findByChainIdAndTransactionHash(ChargeChainFixtures.CHAIN_ID, HASH).orElseThrow();
        var savedEntries = entries.findAllByTransactionIdOrderByIdAsc(transaction.getId());
        assertThat(savedEntries).hasSize(2);
        assertThat(savedEntries).allSatisfy(entry -> {
            assertAmount(entry.getAmountDelta().abs(), expected);
        });
        assertAmount(accounts.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_BALANCE, Long.toString(user.getId())).orElseThrow().getBalance(), expected);
        assertAmount(accounts.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_ISSUANCE, "SYSTEM").orElseThrow().getBalance(), expected.negate());
    }

    @Test
    void 소수_7자리_수량은_변환_정책에서_반올림하지_않고_거부한다() {
        assertThatThrownBy(() -> DepositAmount.of(new BigDecimal("1.0000005")))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.CHARGE_INVALID_AMOUNT));
        assertThat(requests.count()).isZero();
        assertThat(transactions.count()).isZero();
    }

    private void assertAmount(BigDecimal actual, BigDecimal expected) {
        assertThat(actual).isEqualByComparingTo(expected);
        assertThat(actual.scale()).isEqualTo(6);
    }
}
