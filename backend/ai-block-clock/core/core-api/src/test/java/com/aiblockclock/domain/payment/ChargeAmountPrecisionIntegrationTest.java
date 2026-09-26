package com.aiblockclock.domain.payment;

import com.aiblockclock.client.blockchain.SsafyChainClient;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.AfterTransaction;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@Tag("integration")
@SpringBootTest(properties = {
        ChargeChainFixtures.CHAIN_ID_PROPERTY,
        ChargeChainFixtures.CUSTODY_PROPERTY,
        ChargeChainFixtures.TOKEN_PROPERTY,
        ChargeChainFixtures.CONFIRMATIONS_PROPERTY
})
@Transactional
class ChargeAmountPrecisionIntegrationTest {

    private static final String SENDER_ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String TRANSACTION_HASH =
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Autowired
    private ChargeRequestService chargeRequestService;

    @Autowired
    private ChargeProcessingService chargeProcessingService;

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private SsafyChainClient ssafyChainClient;

    private String userReferenceId;

    @AfterTransaction
    void cleanUpCommittedRequest() {
        chargeRequestRepository.findByChainIdAndTransactionHash(12345L, TRANSACTION_HASH)
                .filter(request -> request.getUserReferenceId().equals(userReferenceId))
                .ifPresent(chargeRequestRepository::delete);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.000001", "1.000001", "10.123456"})
    void 소수_6자리_충전이_등록부터_원장과_잔액까지_손실_없이_저장된다(String amount) {
        BigDecimal requestedAmount = new BigDecimal(amount);
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(SENDER_ADDRESS));
        userReferenceId = Long.toString(user.getId());
        given(ssafyChainClient.readTransaction(TRANSACTION_HASH))
                .willReturn(ChargeChainFixtures.deposited(SENDER_ADDRESS, requestedAmount));

        ChargeRequest request = chargeRequestService.createChargeRequest(
                UserId.of(user.getId()),
                TRANSACTION_HASH,
                requestedAmount
        );

        ChargeRequest result = chargeProcessingService.process(request.getId()).chargeRequest();
        entityManager.flush();
        entityManager.clear();

        ChargeRequestEntity savedRequest = chargeRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
        assertThat(savedRequest.getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
        assertExactAmount(savedRequest.getRequestedDepositAmount(), requestedAmount);
        assertExactAmount(savedRequest.getCreditedAbcAmount(), requestedAmount);

        LedgerTransactionEntity transaction = ledgerTransactionRepository
                .findByChainIdAndTransactionHash(ChargeChainFixtures.CHAIN_ID, TRANSACTION_HASH).orElseThrow();
        List<LedgerEntryEntity> entries = ledgerEntryRepository
                .findAllByTransactionIdOrderByIdAsc(transaction.getId());
        assertThat(entries).hasSize(2);
        for (LedgerEntryEntity entry : entries) {
            BigDecimal expectedDelta = entry.getAccount().getAccountType() == LedgerAccountType.ABC_BALANCE
                    ? requestedAmount : requestedAmount.negate();
            assertExactAmount(entry.getAmountDelta(), expectedDelta);
        }
        assertExactAmount(ledgerAccountRepository.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_BALANCE, userReferenceId).orElseThrow().getBalance(), requestedAmount);
        assertExactAmount(ledgerAccountRepository.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_ISSUANCE, "SYSTEM").orElseThrow().getBalance(), requestedAmount.negate());
        assertThat(entries.stream().map(LedgerEntryEntity::getAmountDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private void assertExactAmount(BigDecimal actual, BigDecimal expected) {
        assertThat(actual).isEqualByComparingTo(expected);
        assertThat(actual.scale()).isEqualTo(6);
    }
}
