package com.aiblockclock.api.v1;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AbcControllerTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    private long me;

    private long other;

    private LedgerAccountEntity issuanceAccount;

    @BeforeEach
    void 두_사용자와_발급_계정을_준비한다() {
        me = createUser();
        other = createUser();
        issuanceAccount = saveAccount(LedgerAccountType.ABC_ISSUANCE, "SYSTEM");
    }

    @Test
    void 로그인하지_않으면_잔액을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/credits/balance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 잔액은_쓸_수_있는_금액과_예약_금액을_문자열로_준다() throws Exception {
        LedgerAccountEntity balanceAccount = saveAccount(LedgerAccountType.ABC_BALANCE, referenceIdOf(me));
        LedgerAccountEntity escrowAccount = saveAccount(LedgerAccountType.EXECUTION_ESCROW, referenceIdOf(me));
        addBalance(balanceAccount, "70");
        addBalance(escrowAccount, "30");

        mockMvc.perform(get("/api/v1/credits/balance").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableAmount").value("70.000000"))
                .andExpect(jsonPath("$.data.reservedAmount").value("30.000000"));
    }

    @Test
    void 장부_계정이_없는_사용자의_잔액은_0이다() throws Exception {
        mockMvc.perform(get("/api/v1/credits/balance").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableAmount").value("0.000000"))
                .andExpect(jsonPath("$.data.reservedAmount").value("0.000000"))
                .andExpect(jsonPath("$.data.payoutAmount").value("0.000000"));
    }

    @Test
    void 잔액에는_공급자로서_받은_정산금도_따로_준다() throws Exception {
        LedgerAccountEntity balanceAccount = saveAccount(LedgerAccountType.ABC_BALANCE, referenceIdOf(me));
        LedgerAccountEntity payoutAccount = saveAccount(LedgerAccountType.PAYOUT_BALANCE, referenceIdOf(me));
        addBalance(balanceAccount, "70");
        addBalance(payoutAccount, "12.5");

        mockMvc.perform(get("/api/v1/credits/balance").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableAmount").value("70.000000"))
                .andExpect(jsonPath("$.data.payoutAmount").value("12.500000"));
    }

    @Test
    void 사용_내역은_최신순이고_충전_줄에는_해시_차감_줄에는_실행_ID가_있다() throws Exception {
        LedgerAccountEntity balanceAccount = saveAccount(LedgerAccountType.ABC_BALANCE, referenceIdOf(me));
        LedgerAccountEntity escrowAccount = saveAccount(LedgerAccountType.EXECUTION_ESCROW, referenceIdOf(me));
        recordTransfer(LedgerEventType.CHARGE, "charge-1", "CHARGE_REQUEST", "12", "0xabc123",
                issuanceAccount, balanceAccount, "100.000000");
        recordTransfer(LedgerEventType.RESERVE, "reserve-1", "EXECUTION", "e-1029", null,
                balanceAccount, escrowAccount, "30.000000");

        mockMvc.perform(get("/api/v1/credits/transactions").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].transactionType").value("RESERVE"))
                .andExpect(jsonPath("$.data.content[0].amount").value("-30.000000"))
                .andExpect(jsonPath("$.data.content[0].executionId").value("e-1029"))
                .andExpect(jsonPath("$.data.content[0].transactionHash").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].transactionType").value("CHARGE"))
                .andExpect(jsonPath("$.data.content[1].amount").value("100.000000"))
                .andExpect(jsonPath("$.data.content[1].executionId").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].transactionHash").value("0xabc123"));
    }

    @Test
    void 거래_종류로_걸러_조회한다() throws Exception {
        LedgerAccountEntity balanceAccount = saveAccount(LedgerAccountType.ABC_BALANCE, referenceIdOf(me));
        LedgerAccountEntity escrowAccount = saveAccount(LedgerAccountType.EXECUTION_ESCROW, referenceIdOf(me));
        recordTransfer(LedgerEventType.CHARGE, "charge-1", "CHARGE_REQUEST", "12", "0xabc123",
                issuanceAccount, balanceAccount, "100.000000");
        recordTransfer(LedgerEventType.RESERVE, "reserve-1", "EXECUTION", "e-1029", null,
                balanceAccount, escrowAccount, "30.000000");

        mockMvc.perform(get("/api/v1/credits/transactions").with(as(me))
                        .param("transactionType", "CHARGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].transactionType").value("CHARGE"));
    }

    @Test
    void 커서로_다음_페이지를_이어_조회한다() throws Exception {
        LedgerAccountEntity balanceAccount = saveAccount(LedgerAccountType.ABC_BALANCE, referenceIdOf(me));
        for (int sequence = 1; sequence <= 3; sequence++) {
            recordTransfer(LedgerEventType.CHARGE, "charge-" + sequence, "CHARGE_REQUEST",
                    Integer.toString(sequence), "0xhash" + sequence, issuanceAccount, balanceAccount, "1.000000");
        }

        MvcResult firstPage = mockMvc.perform(get("/api/v1/credits/transactions").with(as(me))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn();

        String nextCursor = mapper.readTree(firstPage.getResponse().getContentAsString())
                .get("data").get("nextCursor").asText();

        mockMvc.perform(get("/api/v1/credits/transactions").with(as(me))
                        .param("size", "2")
                        .param("cursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void 기간을_한쪽만_보내면_거절한다() throws Exception {
        mockMvc.perform(get("/api/v1/credits/transactions").with(as(me))
                        .param("from", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 페이지_크기가_50을_넘으면_거절한다() throws Exception {
        mockMvc.perform(get("/api/v1/credits/transactions").with(as(me))
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 커서가_잘못되면_거절한다() throws Exception {
        mockMvc.perform(get("/api/v1/credits/transactions").with(as(me))
                        .param("cursor", "not-a-cursor!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 다른_사용자의_잔액과_내역은_보이지_않는다() throws Exception {
        LedgerAccountEntity myBalanceAccount = saveAccount(LedgerAccountType.ABC_BALANCE, referenceIdOf(me));
        addBalance(myBalanceAccount, "70");
        recordTransfer(LedgerEventType.CHARGE, "charge-1", "CHARGE_REQUEST", "12", "0xabc123",
                issuanceAccount, myBalanceAccount, "70.000000");

        mockMvc.perform(get("/api/v1/credits/balance").with(as(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableAmount").value("0.000000"));

        mockMvc.perform(get("/api/v1/credits/transactions").with(as(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    private RequestPostProcessor as(long userId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                UserId.of(userId), null, List.of()));
    }

    private long createUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(walletAddress));

        return user.getId();
    }

    // 장부는 사용자를 ID 의 십진 문자열로 기억한다. 가입 시 LedgerAccountWriter 가 쓰는 규칙과 같다.
    private static String referenceIdOf(long userId) {
        return Long.toString(userId);
    }

    private LedgerAccountEntity saveAccount(LedgerAccountType accountType, String referenceId) {
        return ledgerAccountRepository.save(LedgerAccountEntity.of(accountType, referenceId));
    }

    private void addBalance(LedgerAccountEntity account, String amount) {
        ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(amount), Instant.now());
    }

    private void recordTransfer(
            LedgerEventType eventType,
            String idempotencyKey,
            String referenceType,
            String referenceId,
            String transactionHash,
            LedgerAccountEntity fromAccount,
            LedgerAccountEntity toAccount,
            String amount
    ) {
        LedgerTransactionEntity transaction = ledgerTransactionRepository.save(
                LedgerTransactionEntity.of(eventType, idempotencyKey, referenceType, referenceId, null, transactionHash));
        BigDecimal transferAmount = new BigDecimal(amount);

        ledgerEntryRepository.saveAllAndFlush(List.of(
                LedgerEntryEntity.of(transaction, fromAccount, transferAmount.negate()),
                LedgerEntryEntity.of(transaction, toAccount, transferAmount)
        ));
    }
}
