package com.aiblockclock.api.v1;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 기본 정책(1 ~ 1,000 ABC, 24시간에 한 번)을 그대로 쓴다
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WithdrawalControllerTest {
    private static final String WALLET = "0x2222222222222222222222222222222222222222";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Test
    void 신청하면_심사_대기로_받고_묶은_만큼_정산금에서_빠진다() throws Exception {
        long sellerId = sellerWithPayout(WALLET, "30");

        mockMvc.perform(post("/api/v1/withdrawals").with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "12.5")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.withdrawalId").isString())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.amount").value("12.500000"))
                .andExpect(jsonPath("$.data.walletAddress").value(WALLET))
                .andExpect(jsonPath("$.data.requestedAt").exists());

        mockMvc.perform(get("/api/v1/credits/balance").with(as(sellerId)))
                .andExpect(jsonPath("$.data.payoutAmount").value("17.500000"));
    }

    @Test
    void 취소하면_취소_상태로_답하고_정산금이_돌아온다() throws Exception {
        long sellerId = sellerWithPayout(WALLET, "30");
        String withdrawalId = requestAndGetId(sellerId, "10");

        mockMvc.perform(post("/api/v1/withdrawals/{withdrawalId}/cancel", withdrawalId).with(as(sellerId)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawalId").value(withdrawalId))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        mockMvc.perform(get("/api/v1/credits/balance").with(as(sellerId)))
                .andExpect(jsonPath("$.data.payoutAmount").value("30.000000"));
    }

    @Test
    void 범위를_벗어난_금액은_400으로_막고_최소와_최대를_알려_준다() throws Exception {
        long sellerId = sellerWithPayout(WALLET, "5000");

        mockMvc.perform(post("/api/v1/withdrawals").with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "1000.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_010"))
                .andExpect(jsonPath("$.error.data.minimumAmount").value("1.000000"))
                .andExpect(jsonPath("$.error.data.maximumAmount").value("1000.000000"));
    }

    @Test
    void 정산금이_모자라면_409로_막고_지금_정산금을_알려_준다() throws Exception {
        long sellerId = sellerWithPayout(WALLET, "3");

        mockMvc.perform(post("/api/v1/withdrawals").with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "10")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_011"))
                .andExpect(jsonPath("$.error.data.payoutAmount").value("3.000000"));
    }

    @Test
    void 처리_중인_출금이_있으면_409로_막는다() throws Exception {
        long sellerId = sellerWithPayout(WALLET, "30");
        requestAndGetId(sellerId, "10");

        mockMvc.perform(post("/api/v1/withdrawals").with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "10")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_012"));
    }

    @Test
    void 소수_7자리_금액은_400으로_막는다() throws Exception {
        long sellerId = sellerWithPayout(WALLET, "30");

        mockMvc.perform(post("/api/v1/withdrawals").with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "1.0000001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 요청_번호가_없으면_400으로_막는다() throws Exception {
        long sellerId = sellerWithPayout(WALLET, "30");

        mockMvc.perform(post("/api/v1/withdrawals").with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 남의_신청을_취소하면_404로_답한다() throws Exception {
        long sellerId = sellerWithPayout(WALLET, "30");
        long otherId = userRepository.saveAndFlush(UserEntity.of("0x3333333333333333333333333333333333333333")).getId();
        String withdrawalId = requestAndGetId(sellerId, "10");

        mockMvc.perform(post("/api/v1/withdrawals/{withdrawalId}/cancel", withdrawalId).with(as(otherId)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_015"));
    }

    @Test
    void 로그인하지_않으면_신청할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/withdrawals").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "10")))
                .andExpect(status().isUnauthorized());
    }

    private String requestAndGetId(long sellerId, String amount) throws Exception {
        String response = mockMvc.perform(post("/api/v1/withdrawals").with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), amount)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        return response.replaceAll(".*\"withdrawalId\":\"(\\d+)\".*", "$1");
    }

    private long sellerWithPayout(String walletAddress, String payout) {
        long sellerId = userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
        LedgerAccountEntity account = ledgerAccountRepository.save(
                LedgerAccountEntity.of(LedgerAccountType.PAYOUT_BALANCE, Long.toString(sellerId)));
        ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(payout), Instant.now());
        return sellerId;
    }

    private String body(UUID requestId, String amount) {
        return "{\"requestId\":\"%s\",\"amount\":\"%s\"}".formatted(requestId, amount);
    }

    private RequestPostProcessor as(long userId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                UserId.of(userId), null, List.of()));
    }
}
