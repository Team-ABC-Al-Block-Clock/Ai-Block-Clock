package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.SessionCookie;
import com.aiblockclock.storage.auth.WalletAuthMessageRepository;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {
    private static final String ROOT = "/api/v1/auth";
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WalletAuthMessageRepository messageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void 서버_시각을_고정한다() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void 로그인은_Spring_Session을_MySQL에_저장하고_HttpOnly_쿠키만_전달한다() throws Exception {
        MvcResult result = login(newWallet());
        Cookie cookie = result.getResponse().getCookie(SessionCookie.NAME);

        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getPath()).isEqualTo("/api/v1");
        assertThat(cookie.getMaxAge()).isEqualTo(7 * 24 * 60 * 60);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Lax");
        assertThat(result.getResponse().getContentAsString())
                .contains("2026-09-24T00:00:00Z")
                .doesNotContain(cookie.getValue(), "accessToken", "refreshToken");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from SPRING_SESSION where SESSION_ID = ?",
                Integer.class,
                cookie.getValue()
        )).isEqualTo(1);

        mvc.perform(get("/api/v1/workflows").cookie(cookie))
                .andExpect(status().isOk());
    }

    @Test
    void 로그아웃은_Spring_Session을_삭제하고_브라우저_쿠키를_삭제한다() throws Exception {
        Cookie cookie = login(newWallet()).getResponse().getCookie(SessionCookie.NAME);

        MvcResult logout = mvc.perform(post(ROOT + "/logout")
                        .with(csrf().asHeader())
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cleared = logout.getResponse().getCookie(SessionCookie.NAME);
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getPath()).isEqualTo("/api/v1");
        mvc.perform(get("/api/v1/workflows").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
    }

    @Test
    void CSRF_토큰을_발급하고_상태_변경_요청에서_검증한다() throws Exception {
        String requestBody = mapper.writeValueAsString(Map.of("walletAddress", address(newWallet())));

        MvcResult issued = mvc.perform(get(ROOT + "/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        Cookie sessionCookie = issued.getResponse().getCookie(SessionCookie.NAME);
        var csrfData = mapper.readTree(issued.getResponse().getContentAsString()).path("data");
        String headerName = csrfData.path("headerName").asString();
        String token = csrfData.path("token").asString();

        assertThat(sessionCookie).isNotNull();

        mvc.perform(post(ROOT + "/wallet/message")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isForbidden());
        mvc.perform(post(ROOT + "/wallet/message")
                        .cookie(sessionCookie)
                        .header(headerName, "invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isForbidden());
        mvc.perform(post(ROOT + "/wallet/message")
                        .cookie(sessionCookie)
                        .header(headerName, token)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void 세션_쿠키가_없거나_DB에_없는_세션이면_보호_API를_사용할_수_없다() throws Exception {
        mvc.perform(get("/api/v1/workflows"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
        mvc.perform(get("/api/v1/workflows")
                        .cookie(new Cookie(SessionCookie.NAME, "bad")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
    }

    @Test
    void 같은_사용자가_다시_로그인하면_이전_Spring_Session은_종료된다() throws Exception {
        ECKeyPair wallet = newWallet();
        Cookie first = login(wallet).getResponse().getCookie(SessionCookie.NAME);
        Cookie second = login(wallet, first).getResponse().getCookie(SessionCookie.NAME);

        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        mvc.perform(get("/api/v1/workflows").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
        mvc.perform(get("/api/v1/workflows").cookie(second))
                .andExpect(status().isOk());
        assertThat(sessionCount(first)).isZero();
        assertThat(sessionCount(second)).isEqualTo(1);
    }

    @Test
    void 로그인_후_고정_7일이_지나면_사용_중이어도_세션이_만료된다() throws Exception {
        Cookie cookie = login(newWallet()).getResponse().getCookie(SessionCookie.NAME);
        when(clock.instant()).thenReturn(NOW.plusSeconds(7 * 24 * 60 * 60));

        mvc.perform(get("/api/v1/workflows").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_013"));
    }

    @Test
    void 재발급_API는_없고_Swagger는_세션_쿠키_인증만_안내한다() throws Exception {
        MvcResult docs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var root = mapper.readTree(docs.getResponse().getContentAsString());
        var paths = root.path("paths");
        assertThat(paths.has(ROOT + "/reissue")).isFalse();
        assertThat(root.path("components").path("securitySchemes")
                .path(SessionCookie.NAME).path("in").asString()).isEqualTo("cookie");
        assertThat(paths.path(ROOT + "/wallet/message").path("post").path("security").size()).isZero();
        assertThat(paths.path(ROOT + "/wallet/login").path("post").path("security").size()).isZero();
        assertThat(root.path("security").get(0).has(SessionCookie.NAME)).isTrue();
    }

    private MvcResult login(ECKeyPair wallet) throws Exception {
        return login(wallet, null);
    }

    private MvcResult login(ECKeyPair wallet, Cookie currentSession) throws Exception {
        String walletAddress = address(wallet);
        MockHttpServletRequestBuilder messageRequest = post(ROOT + "/wallet/message")
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("walletAddress", walletAddress)));
        if (currentSession != null) {
            messageRequest.cookie(currentSession);
        }
        MvcResult issued = mvc.perform(messageRequest)
                .andExpect(status().isOk())
                .andReturn();
        String message = mapper.readTree(issued.getResponse().getContentAsString())
                .path("data").path("message").asString();
        assertThat(message).isEqualTo(messageRepository.findByWalletAddress(walletAddress).orElseThrow().getMessage());

        MockHttpServletRequestBuilder loginRequest = post(ROOT + "/wallet/login")
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "walletAddress", walletAddress,
                        "signature", sign(message, wallet)
                )));
        if (currentSession != null) {
            loginRequest.cookie(currentSession);
        }

        return mvc.perform(loginRequest)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
    }

    private int sessionCount(Cookie cookie) {
        return jdbcTemplate.queryForObject(
                "select count(*) from SPRING_SESSION where SESSION_ID = ?",
                Integer.class,
                cookie.getValue()
        );
    }

    private ECKeyPair newWallet() {
        return ECKeyPair.create(new BigInteger(
                UUID.randomUUID().toString().replace("-", ""), 16
        ).add(BigInteger.ONE));
    }

    private String address(ECKeyPair wallet) {
        return "0x" + Keys.getAddress(wallet.getPublicKey());
    }

    private String sign(String message, ECKeyPair wallet) {
        Sign.SignatureData signed = Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), wallet);
        HexFormat hex = HexFormat.of();
        return "0x" + hex.formatHex(signed.getR())
                + hex.formatHex(signed.getS()) + hex.formatHex(signed.getV());
    }
}
