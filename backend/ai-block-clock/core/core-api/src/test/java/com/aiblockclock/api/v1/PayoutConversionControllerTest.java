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

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PayoutConversionControllerTest {
    private static final String PATH = "/api/v1/credits/payout-conversions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Test
    void 옮기면_옮긴_금액과_옮긴_뒤_잔액을_답한다() throws Exception {
        long sellerId = sellerWithPayout("0x4444444444444444444444444444444444444401", "30");

        mockMvc.perform(post(PATH).with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "12.5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.convertedAmount").value("12.500000"))
                .andExpect(jsonPath("$.data.balance.availableAmount").value("12.500000"))
                .andExpect(jsonPath("$.data.balance.reservedAmount").value("0.000000"))
                .andExpect(jsonPath("$.data.balance.payoutAmount").value("17.500000"));
    }

    @Test
    void 옮긴_금액은_ABC_사용_내역에_정산금_옮기기로_남는다() throws Exception {
        long sellerId = sellerWithPayout("0x4444444444444444444444444444444444444402", "30");
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post(PATH).with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(requestId, "10")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/credits/transactions").with(as(sellerId)))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].transactionType").value("PAYOUT_CONVERSION"))
                .andExpect(jsonPath("$.data.content[0].amount").value("10.000000"))
                .andExpect(jsonPath("$.data.content[0].executionId").doesNotExist());
    }

    @Test
    void 같은_요청_번호로_다시_보내면_처음_옮긴_금액을_답하고_또_옮기지_않는다() throws Exception {
        long sellerId = sellerWithPayout("0x4444444444444444444444444444444444444403", "30");
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post(PATH).with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(requestId, "10")))
                .andExpect(status().isOk());

        mockMvc.perform(post(PATH).with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(requestId, "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.convertedAmount").value("10.000000"))
                .andExpect(jsonPath("$.data.balance.payoutAmount").value("20.000000"));
    }

    @Test
    void 정산금이_모자라면_409로_막고_지금_정산금을_알려_준다() throws Exception {
        long sellerId = sellerWithPayout("0x4444444444444444444444444444444444444404", "3");

        mockMvc.perform(post(PATH).with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "10")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_018"))
                .andExpect(jsonPath("$.error.data.payoutAmount").value("3.000000"));
    }

    @Test
    void 영_이하의_금액은_400으로_막는다() throws Exception {
        long sellerId = sellerWithPayout("0x4444444444444444444444444444444444444405", "30");

        mockMvc.perform(post(PATH).with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 소수_7자리_금액은_400으로_막는다() throws Exception {
        long sellerId = sellerWithPayout("0x4444444444444444444444444444444444444406", "30");

        mockMvc.perform(post(PATH).with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "1.0000001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 요청_번호가_없으면_400으로_막는다() throws Exception {
        long sellerId = sellerWithPayout("0x4444444444444444444444444444444444444407", "30");

        mockMvc.perform(post(PATH).with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 로그인하지_않으면_옮길_수_없다() throws Exception {
        mockMvc.perform(post(PATH).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "10")))
                .andExpect(status().isUnauthorized());
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
