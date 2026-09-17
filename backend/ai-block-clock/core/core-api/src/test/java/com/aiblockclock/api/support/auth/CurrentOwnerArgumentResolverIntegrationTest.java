package com.aiblockclock.api.support.auth;

import com.aiblockclock.domain.auth.AccessTokenGenerator;
import com.aiblockclock.domain.auth.LoginSession;
import com.aiblockclock.domain.auth.LoginSessionId;
import com.aiblockclock.domain.auth.LoginSessionWriter;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.auth.LoginSessionEntity;
import com.aiblockclock.storage.auth.LoginSessionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CurrentOwnerArgumentResolverIntegrationTest {
    private static final String WORKFLOWS = "/api/v1/workflows";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginSessionRepository loginSessionRepository;

    @Autowired
    private LoginSessionWriter loginSessionWriter;

    @Autowired
    private AccessTokenGenerator accessTokenGenerator;

    @Autowired
    private Clock clock;

    @ParameterizedTest
    @ValueSource(strings = {"Bearer", "bearer", "BEARER", "bEaReR"})
    void 인증_방식의_대소문자와_관계없이_유효한_토큰을_처리한다(String scheme) throws Exception {
        LoginSessionEntity session = saveSession();
        String header = scheme + bearerToken(session).substring("Bearer".length());

        mockMvc.perform(get(WORKFLOWS).header(HttpHeaders.AUTHORIZATION, header))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void Authorization_헤더가_없으면_요청자를_확인할_수_없다() throws Exception {
        mockMvc.perform(get(WORKFLOWS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer", "Basic dXNlcjpwYXNz", "Bearer\tinvalid", "Bearer   "})
    void Bearer_형식이_아니거나_토큰이_비어_있으면_거절한다(String header) throws Exception {
        mockMvc.perform(get(WORKFLOWS).header(HttpHeaders.AUTHORIZATION, header))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_019"));
    }

    @Test
    void 서명이_올바르지_않은_토큰은_거절한다() throws Exception {
        mockMvc.perform(get(WORKFLOWS).header(HttpHeaders.AUTHORIZATION, "Bearer a.b.c"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_019"));
    }

    @Test
    void 더_이상_X_Owner_Id_헤더로는_요청자를_식별하지_않는다() throws Exception {
        LoginSessionEntity session = saveSession();

        mockMvc.perform(get(WORKFLOWS).header("X-Owner-Id", session.getUserId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
    }

    @Test
    void 종료된_세션의_토큰으로는_더_이상_요청할_수_없다() throws Exception {
        LoginSessionEntity session = saveSession();
        String header = bearerToken(session);
        loginSessionWriter.modifyAsRevoked(LoginSessionId.of(session.getId()));

        mockMvc.perform(get(WORKFLOWS).header(HttpHeaders.AUTHORIZATION, header))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_014"));
    }

    private LoginSessionEntity saveSession() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(walletAddress));

        return loginSessionRepository.saveAndFlush(LoginSessionEntity.of(
                user.getId(), clock.instant().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS)
        ));
    }

    private String bearerToken(LoginSessionEntity session) {
        LoginSession loginSession = new LoginSession(
                LoginSessionId.of(session.getId()),
                UserId.of(session.getUserId()),
                session.getExpiresAt(),
                session.getRevokedAt()
        );

        return "Bearer " + accessTokenGenerator.generate(loginSession).value();
    }
}
