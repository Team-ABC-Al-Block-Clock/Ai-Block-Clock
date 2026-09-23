package com.aiblockclock.api.v1;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {
    private static final String WALLET_ADDRESS = "0x8888888888888888888888888888888888888888";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 로그인하지_않으면_내_정보를_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
    }

    @Test
    void 가입_전_사용자는_이름이_비어_있고_가입_미완료로_응답한다() throws Exception {
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(WALLET_ADDRESS));

        MvcResult result = mockMvc.perform(get("/api/v1/users/me").with(as(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(Long.toString(user.getId())))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andReturn();

        // 화면은 키가 빠진 것(undefined)과 null 을 다르게 다루므로 키가 null 로 남아 있어야 한다.
        JsonNode data = mapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(List.of("nickname", "introduction", "profileImageUrl")).allSatisfy(field -> {
            assertThat(data.has(field)).isTrue();
            assertThat(data.get(field).isNull()).isTrue();
        });
    }

    @Test
    void 지갑은_중첩_객체로_종류_주소_네트워크_체인_ID를_담는다() throws Exception {
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(WALLET_ADDRESS));

        mockMvc.perform(get("/api/v1/users/me").with(as(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wallet.provider").value("METAMASK"))
                .andExpect(jsonPath("$.data.wallet.address").value(WALLET_ADDRESS))
                .andExpect(jsonPath("$.data.wallet.network").value("POLYGON_AMOY"))
                .andExpect(jsonPath("$.data.wallet.chainId").value(80002));
    }

    @Test
    void 가입한_사용자는_이름_소개와_가입_완료로_응답한다() throws Exception {
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(WALLET_ADDRESS));
        user.completeOnboarding("김싸피", "에이전트를 만듭니다", Instant.parse("2026-09-23T01:02:03Z"));
        userRepository.flush();

        mockMvc.perform(get("/api/v1/users/me").with(as(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("김싸피"))
                .andExpect(jsonPath("$.data.introduction").value("에이전트를 만듭니다"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));
    }

    @Test
    void 세션의_사용자가_없으면_요청자_정보가_올바르지_않다고_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").with(as(Long.MAX_VALUE)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_006"));
    }

    private RequestPostProcessor as(long userId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                UserId.of(userId), null, List.of()));
    }
}
