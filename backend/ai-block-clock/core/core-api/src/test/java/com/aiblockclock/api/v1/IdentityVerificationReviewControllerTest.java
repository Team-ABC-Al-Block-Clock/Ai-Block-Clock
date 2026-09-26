package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.SessionAuthenticationManager;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.identity.IdentityVerificationStatus;
import com.aiblockclock.storage.identity.IdentityVerificationEntity;
import com.aiblockclock.storage.identity.IdentityVerificationRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.contains;
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
class IdentityVerificationReviewControllerTest {
    private static final String ROOT = "/api/v1/admin/identity-verifications";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityVerificationRepository identityVerificationRepository;

    @Test
    void 관리자는_심사_대기만_오래_기다린_것부터_본다() throws Exception {
        long adminId = user("0x5000000000000000000000000000000000000001");
        long older = verification(user("0x5000000000000000000000000000000000000002"),
                IdentityVerificationStatus.SUBMITTED, Instant.parse("2026-09-26T01:00:00Z"));
        long newer = verification(user("0x5000000000000000000000000000000000000003"),
                IdentityVerificationStatus.SUBMITTED, Instant.parse("2026-09-26T02:00:00Z"));
        verification(user("0x5000000000000000000000000000000000000004"),
                IdentityVerificationStatus.APPROVED, Instant.parse("2026-09-26T00:00:00Z"));

        mockMvc.perform(get(ROOT).with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].verificationId")
                        .value(contains(Long.toString(older), Long.toString(newer))));
    }

    @Test
    void 승인하면_승인_상태와_심사_시각을_답한다() throws Exception {
        long adminId = user("0x5000000000000000000000000000000000000005");
        long verificationId = verification(user("0x5000000000000000000000000000000000000006"),
                IdentityVerificationStatus.SUBMITTED, Instant.now());

        mockMvc.perform(post(ROOT + "/{verificationId}/approve", verificationId).with(asAdmin(adminId)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedAt").exists());
    }

    @Test
    void 반려하면_사유가_남아_사용자가_본다() throws Exception {
        long adminId = user("0x5000000000000000000000000000000000000007");
        long userId = user("0x5000000000000000000000000000000000000008");
        long verificationId = verification(userId, IdentityVerificationStatus.SUBMITTED, Instant.now());

        mockMvc.perform(post(ROOT + "/{verificationId}/reject", verificationId).with(asAdmin(adminId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"실명이 다릅니다\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("실명이 다릅니다"));

        mockMvc.perform(get("/api/v1/users/me/identity-verification").with(as(userId)))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("실명이 다릅니다"));
    }

    @Test
    void 반려_사유가_비면_400으로_막는다() throws Exception {
        long adminId = user("0x5000000000000000000000000000000000000009");
        long verificationId = verification(user("0x500000000000000000000000000000000000000a"),
                IdentityVerificationStatus.SUBMITTED, Instant.now());

        mockMvc.perform(post(ROOT + "/{verificationId}/reject", verificationId).with(asAdmin(adminId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 반려된_것을_승인하면_409로_막는다() throws Exception {
        long adminId = user("0x500000000000000000000000000000000000000b");
        long verificationId = verification(user("0x500000000000000000000000000000000000000c"),
                IdentityVerificationStatus.REJECTED, Instant.now());

        mockMvc.perform(post(ROOT + "/{verificationId}/approve", verificationId).with(asAdmin(adminId)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_010"));
    }

    @Test
    void 없는_본인_확인은_404로_답한다() throws Exception {
        long adminId = user("0x500000000000000000000000000000000000000d");

        mockMvc.perform(post(ROOT + "/{verificationId}/approve", 987_654L).with(asAdmin(adminId)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_009"));
    }

    @Test
    void 관리자가_아니면_목록도_심사도_403으로_막는다() throws Exception {
        long userId = user("0x500000000000000000000000000000000000000e");
        long verificationId = verification(userId, IdentityVerificationStatus.SUBMITTED, Instant.now());

        mockMvc.perform(get(ROOT).with(as(userId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ROOT + "/{verificationId}/approve", verificationId).with(as(userId)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 로그인하지_않으면_401로_답한다() throws Exception {
        mockMvc.perform(get(ROOT))
                .andExpect(status().isUnauthorized());
    }

    private long user(String walletAddress) {
        return userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
    }

    private long verification(long userId, IdentityVerificationStatus status, Instant submittedAt) {
        return identityVerificationRepository.saveAndFlush(IdentityVerificationEntity.of(
                userId, "김현수", LocalDate.of(1999, 3, 15), status, submittedAt)).getId();
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
