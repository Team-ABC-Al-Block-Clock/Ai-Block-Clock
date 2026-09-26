package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.SessionAuthenticationManager;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.identity.IdentityVerificationStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.identity.IdentityVerificationEntity;
import com.aiblockclock.storage.identity.IdentityVerificationRepository;
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
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
class WithdrawalReviewControllerTest {
    private static final String ROOT = "/api/v1/admin/withdrawals";
    private static final String SELLER_WALLET = "0x6000000000000000000000000000000000000002";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private IdentityVerificationRepository identityVerificationRepository;

    @Test
    void 관리자는_심사_대기_신청을_신청한_사람의_본인_확인과_함께_본다() throws Exception {
        long adminId = user("0x6000000000000000000000000000000000000001");
        long sellerId = verifiedSellerWithPayout(SELLER_WALLET, "30");
        String withdrawalId = request(sellerId, "10");

        mockMvc.perform(get(ROOT).with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].withdrawal.withdrawalId").value(withdrawalId))
                .andExpect(jsonPath("$.data.items[0].withdrawal.amount").value("10.000000"))
                .andExpect(jsonPath("$.data.items[0].withdrawal.walletAddress").value(SELLER_WALLET))
                .andExpect(jsonPath("$.data.items[0].requesterId").value(Long.toString(sellerId)))
                .andExpect(jsonPath("$.data.items[0].identity.legalName").value("판매자"))
                .andExpect(jsonPath("$.data.items[0].identity.status").value("APPROVED"));
    }

    @Test
    void 승인하면_지급_대기로_답하고_금액은_계속_묶여_있다() throws Exception {
        long adminId = user("0x6000000000000000000000000000000000000003");
        long sellerId = verifiedSellerWithPayout(SELLER_WALLET, "30");
        String withdrawalId = request(sellerId, "10");

        mockMvc.perform(post(ROOT + "/{withdrawalId}/approve", withdrawalId).with(asAdmin(adminId)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedAt").exists())
                .andExpect(jsonPath("$.data.rejectReason").doesNotExist());

        mockMvc.perform(get("/api/v1/credits/balance").with(as(sellerId)))
                .andExpect(jsonPath("$.data.payoutAmount").value("20.000000"));
        mockMvc.perform(get(ROOT).with(asAdmin(adminId)))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 반려하면_사유를_답하고_정산금이_돌아온다() throws Exception {
        long adminId = user("0x6000000000000000000000000000000000000004");
        long sellerId = verifiedSellerWithPayout(SELLER_WALLET, "30");
        String withdrawalId = request(sellerId, "10");

        mockMvc.perform(post(ROOT + "/{withdrawalId}/reject", withdrawalId).with(asAdmin(adminId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"정산 내역 확인이 필요합니다\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("정산 내역 확인이 필요합니다"));

        mockMvc.perform(get("/api/v1/credits/balance").with(as(sellerId)))
                .andExpect(jsonPath("$.data.payoutAmount").value("30.000000"));
    }

    @Test
    void 공급자가_취소한_신청을_승인하면_409로_막는다() throws Exception {
        long adminId = user("0x6000000000000000000000000000000000000005");
        long sellerId = verifiedSellerWithPayout(SELLER_WALLET, "30");
        String withdrawalId = request(sellerId, "10");
        mockMvc.perform(post("/api/v1/withdrawals/{withdrawalId}/cancel", withdrawalId).with(as(sellerId)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post(ROOT + "/{withdrawalId}/approve", withdrawalId).with(asAdmin(adminId)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_016"));
    }

    @Test
    void 반려_사유가_비면_400으로_막는다() throws Exception {
        long adminId = user("0x6000000000000000000000000000000000000006");
        long sellerId = verifiedSellerWithPayout(SELLER_WALLET, "30");
        String withdrawalId = request(sellerId, "10");

        mockMvc.perform(post(ROOT + "/{withdrawalId}/reject", withdrawalId).with(asAdmin(adminId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 없는_신청은_404로_답한다() throws Exception {
        long adminId = user("0x6000000000000000000000000000000000000007");

        mockMvc.perform(post(ROOT + "/{withdrawalId}/approve", 987_654L).with(asAdmin(adminId)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_015"));
    }

    @Test
    void 관리자가_아니면_자기_신청이라도_승인할_수_없다() throws Exception {
        long sellerId = verifiedSellerWithPayout(SELLER_WALLET, "30");
        String withdrawalId = request(sellerId, "10");

        mockMvc.perform(get(ROOT).with(as(sellerId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ROOT + "/{withdrawalId}/approve", withdrawalId).with(as(sellerId)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void CSRF_토큰이_없으면_관리자라도_승인할_수_없다() throws Exception {
        long adminId = user("0x6000000000000000000000000000000000000008");
        long sellerId = verifiedSellerWithPayout(SELLER_WALLET, "30");
        String withdrawalId = request(sellerId, "10");

        mockMvc.perform(post(ROOT + "/{withdrawalId}/approve", withdrawalId).with(asAdmin(adminId)))
                .andExpect(status().isForbidden());
    }

    private String request(long sellerId, String amount) throws Exception {
        String response = mockMvc.perform(post("/api/v1/withdrawals").with(as(sellerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"%s\",\"amount\":\"%s\"}".formatted(UUID.randomUUID(), amount)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        return response.replaceAll(".*\"withdrawalId\":\"(\\d+)\".*", "$1");
    }

    private long verifiedSellerWithPayout(String walletAddress, String payout) {
        long sellerId = user(walletAddress);
        identityVerificationRepository.saveAndFlush(IdentityVerificationEntity.of(
                sellerId, "판매자", LocalDate.of(1990, 1, 1), IdentityVerificationStatus.APPROVED, Instant.now()));
        LedgerAccountEntity account = ledgerAccountRepository.save(
                LedgerAccountEntity.of(LedgerAccountType.PAYOUT_BALANCE, Long.toString(sellerId)));
        ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(payout), Instant.now());
        return sellerId;
    }

    private long user(String walletAddress) {
        return userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
    }

    private RequestPostProcessor as(long userId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                UserId.of(userId), null, List.of()));
    }

    private RequestPostProcessor asAdmin(long userId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                UserId.of(userId), null,
                AuthorityUtils.createAuthorityList("ROLE_" + SessionAuthenticationManager.ADMIN_ROLE)));
    }
}
