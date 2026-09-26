package com.aiblockclock.api.v1;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
                .andExpect(jsonPath("$.data.wallet.network").value("SSAFY"))
                .andExpect(jsonPath("$.data.wallet.chainId").value(31221));
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

    @Test
    void 이름_소개와_두_동의를_보내면_가입을_마치고_내_정보를_돌려준다() throws Exception {
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(WALLET_ADDRESS));

        mockMvc.perform(signupRequest(user.getId(), "  김싸피  ", "에이전트를 만듭니다", "true", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(Long.toString(user.getId())))
                .andExpect(jsonPath("$.data.nickname").value("김싸피"))
                .andExpect(jsonPath("$.data.introduction").value("에이전트를 만듭니다"))
                .andExpect(jsonPath("$.data.wallet.address").value(WALLET_ADDRESS))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));

        mockMvc.perform(get("/api/v1/users/me").with(as(user.getId())))
                .andExpect(jsonPath("$.data.nickname").value("김싸피"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));
    }

    @Test
    void 소개를_보내지_않으면_소개_없이_가입한다() throws Exception {
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(WALLET_ADDRESS));

        MvcResult result = mockMvc.perform(signupRequest(user.getId(), "김싸피", null, "true", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = mapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.get("introduction").isNull()).isTrue();
    }

    @Test
    void 프로필_이미지를_실어_보내도_가입은_되고_이미지는_아직_저장하지_않는다() throws Exception {
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(WALLET_ADDRESS));
        MockMultipartFile image = new MockMultipartFile(
                "profileImageFile", "me.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});

        MvcResult result = mockMvc.perform(signupRequest(user.getId(), "김싸피", null, "true", "true").file(image))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = mapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.get("profileImageUrl").isNull()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(value = {"김, NULL", "가나다라마바사아자차카타파하가나다라마바사, NULL", "김싸피, OVER_40"}, nullValues = "NULL")
    void 이름이_2에서_20자가_아니거나_소개가_40자를_넘으면_USER_002로_거절한다(String nickname, String introduction)
            throws Exception {
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(WALLET_ADDRESS));
        String actualIntroduction = "OVER_40".equals(introduction) ? "가".repeat(41) : introduction;

        mockMvc.perform(signupRequest(user.getId(), nickname, actualIntroduction, "true", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_002"));
    }

    @ParameterizedTest
    @CsvSource(value = {"false, true", "true, false", "NULL, true", "true, NULL"}, nullValues = "NULL")
    void 두_동의_중_하나라도_true가_아니면_USER_003으로_거절한다(String termsAgreed, String recoveryNoticeConfirmed)
            throws Exception {
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(WALLET_ADDRESS));

        mockMvc.perform(signupRequest(user.getId(), "김싸피", null, termsAgreed, recoveryNoticeConfirmed))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_003"));
    }

    @Test
    void 이미_가입한_사용자가_다시_보내면_USER_006으로_거절하고_이름을_덮지_않는다() throws Exception {
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(WALLET_ADDRESS));
        mockMvc.perform(signupRequest(user.getId(), "김싸피", null, "true", "true"))
                .andExpect(status().isOk());

        mockMvc.perform(signupRequest(user.getId(), "박싸피", null, "true", "true"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_006"));

        mockMvc.perform(get("/api/v1/users/me").with(as(user.getId())))
                .andExpect(jsonPath("$.data.nickname").value("김싸피"));
    }

    @Test
    void 로그인하지_않으면_가입할_수_없다() throws Exception {
        mockMvc.perform(multipart("/api/v1/users/me/signup")
                        .param("nickname", "김싸피")
                        .param("termsAgreed", "true")
                        .param("recoveryNoticeConfirmed", "true")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
    }

    private MockMultipartHttpServletRequestBuilder signupRequest(
            long userId,
            String nickname,
            String introduction,
            String termsAgreed,
            String recoveryNoticeConfirmed
    ) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/users/me/signup");
        putIfPresent(request, "nickname", nickname);
        putIfPresent(request, "introduction", introduction);
        putIfPresent(request, "termsAgreed", termsAgreed);
        putIfPresent(request, "recoveryNoticeConfirmed", recoveryNoticeConfirmed);
        request.with(as(userId)).with(csrf());

        return request;
    }

    // 화면은 값이 없으면 파트 자체를 보내지 않는다.
    private static void putIfPresent(MockMultipartHttpServletRequestBuilder request, String name, String value) {
        if (value != null) {
            request.param(name, value);
        }
    }

    private RequestPostProcessor as(long userId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                UserId.of(userId), null, List.of()));
    }
}
