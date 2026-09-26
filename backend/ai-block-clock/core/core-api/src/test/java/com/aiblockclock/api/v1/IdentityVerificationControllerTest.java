package com.aiblockclock.api.v1;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
class IdentityVerificationControllerTest {
    private static final String ROOT = "/api/v1/users/me/identity-verification";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityVerificationRepository identityVerificationRepository;

    @Test
    void 제출하면_심사_대기로_받고_내_본인_확인으로_조회된다() throws Exception {
        long userId = user("0x4000000000000000000000000000000000000001");

        mockMvc.perform(post(ROOT).with(as(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("김현수", "1999-03-15")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.verificationId").isString())
                .andExpect(jsonPath("$.data.userId").value(Long.toString(userId)))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.legalName").value("김현수"))
                .andExpect(jsonPath("$.data.birthDate").value("1999-03-15"))
                .andExpect(jsonPath("$.data.submittedAt").exists())
                .andExpect(jsonPath("$.data.reviewedAt").doesNotExist());

        mockMvc.perform(get(ROOT).with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    void 제출한_적이_없으면_404로_답한다() throws Exception {
        long userId = user("0x4000000000000000000000000000000000000002");

        mockMvc.perform(get(ROOT).with(as(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_009"));
    }

    @Test
    void 심사_중에_다시_내면_409로_막는다() throws Exception {
        long userId = user("0x4000000000000000000000000000000000000003");
        verification(userId, IdentityVerificationStatus.SUBMITTED);

        mockMvc.perform(post(ROOT).with(as(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("김현수", "1999-03-15")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_007"));
    }

    @Test
    void 승인된_뒤에_다시_내면_409로_막는다() throws Exception {
        long userId = user("0x4000000000000000000000000000000000000004");
        verification(userId, IdentityVerificationStatus.APPROVED);

        mockMvc.perform(post(ROOT).with(as(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("김현수", "1999-03-15")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_008"));
    }

    @Test
    void 반려된_뒤에는_고쳐_다시_내면_심사_대기로_돌아간다() throws Exception {
        long userId = user("0x4000000000000000000000000000000000000005");
        verification(userId, IdentityVerificationStatus.REJECTED);

        mockMvc.perform(post(ROOT).with(as(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("김현수", "1999-03-16")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.birthDate").value("1999-03-16"))
                .andExpect(jsonPath("$.data.rejectReason").doesNotExist());
    }

    @Test
    void 실명이_비었거나_생년월일이_오늘_이후면_400으로_막는다() throws Exception {
        long userId = user("0x4000000000000000000000000000000000000006");

        mockMvc.perform(post(ROOT).with(as(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(" ", "1999-03-15")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        mockMvc.perform(post(ROOT).with(as(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("김현수", LocalDate.now().plusDays(1).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 로그인하지_않으면_제출할_수_없다() throws Exception {
        mockMvc.perform(post(ROOT).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("김현수", "1999-03-15")))
                .andExpect(status().isUnauthorized());
    }

    private long user(String walletAddress) {
        return userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
    }

    private void verification(long userId, IdentityVerificationStatus status) {
        identityVerificationRepository.saveAndFlush(IdentityVerificationEntity.of(
                userId, "김현수", LocalDate.of(1999, 3, 15), status, Instant.now()));
    }

    private String body(String legalName, String birthDate) {
        return "{\"legalName\":\"%s\",\"birthDate\":\"%s\"}".formatted(legalName, birthDate);
    }

    private RequestPostProcessor as(long userId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                UserId.of(userId), null, List.of()));
    }
}
