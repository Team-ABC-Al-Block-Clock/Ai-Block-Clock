package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.SessionTokenCookie;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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
    private static final String ORIGIN = "http://localhost:5173";
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WalletAuthMessageRepository messageRepository;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void 서버_시각을_고정한다() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void 로그인은_세션_원문을_JSON에_노출하지_않고_HttpOnly_쿠키로만_전달한다() throws Exception {
        MvcResult result = login(newWallet());
        Cookie cookie = result.getResponse().getCookie(SessionTokenCookie.NAME);

        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).matches("[0-9a-f]{64}");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getPath()).isEqualTo("/api/v1");
        assertThat(cookie.getMaxAge()).isEqualTo(7 * 24 * 60 * 60);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Lax");
        assertThat(result.getResponse().getContentAsString())
                .contains("2026-09-24T00:00:00Z")
                .doesNotContain(cookie.getValue(), "accessToken", "refreshToken");

        mvc.perform(get("/api/v1/workflows").cookie(cookie))
                .andExpect(status().isOk());
    }

    @Test
    void 로그아웃은_DB_세션을_종료하고_브라우저_쿠키를_삭제한다() throws Exception {
        Cookie cookie = login(newWallet()).getResponse().getCookie(SessionTokenCookie.NAME);

        MvcResult logout = mvc.perform(post(ROOT + "/logout")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cleared = logout.getResponse().getCookie(SessionTokenCookie.NAME);
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getPath()).isEqualTo("/api/v1");
        mvc.perform(get("/api/v1/workflows").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_014"));
    }

    @Test
    void 상태를_바꾸는_API는_허용된_Origin이나_Referer가_필요하다() throws Exception {
        String requestBody = mapper.writeValueAsString(Map.of("walletAddress", address(newWallet())));

        mvc.perform(post(ROOT + "/wallet/message")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest());
        mvc.perform(post(ROOT + "/wallet/message")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isForbidden());
        mvc.perform(post(ROOT + "/wallet/message")
                        .header(HttpHeaders.REFERER, ORIGIN + "/login")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void 세션_쿠키가_없거나_잘못됐거나_DB에_없으면_보호_API를_사용할_수_없다() throws Exception {
        mvc.perform(get("/api/v1/workflows"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
        mvc.perform(get("/api/v1/workflows")
                        .cookie(new Cookie(SessionTokenCookie.NAME, "bad")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_018"));
        mvc.perform(get("/api/v1/workflows")
                        .cookie(new Cookie(SessionTokenCookie.NAME, "a".repeat(64))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_018"));
    }

    @Test
    void FE의_CORS_사전_요청은_쿠키_전송을_허용하고_Authorization_헤더는_요구하지_않는다() throws Exception {
        mvc.perform(options(ROOT + "/wallet/login")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void 재발급_API는_없고_Swagger는_세션_쿠키_인증만_안내한다() throws Exception {
        MvcResult docs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var root = mapper.readTree(docs.getResponse().getContentAsString());
        var paths = root.path("paths");
        assertThat(paths.has(ROOT + "/reissue")).isFalse();
        assertThat(root.path("components").path("securitySchemes")
                .path(SessionTokenCookie.NAME).path("in").asString()).isEqualTo("cookie");
        assertThat(paths.path(ROOT + "/wallet/message").path("post").path("security").size()).isZero();
        assertThat(paths.path(ROOT + "/wallet/login").path("post").path("security").size()).isZero();
        assertThat(root.path("security").get(0).has(SessionTokenCookie.NAME)).isTrue();
    }

    private MvcResult login(ECKeyPair wallet) throws Exception {
        String walletAddress = address(wallet);
        MvcResult issued = mvc.perform(post(ROOT + "/wallet/message")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("walletAddress", walletAddress))))
                .andExpect(status().isOk())
                .andReturn();
        String message = mapper.readTree(issued.getResponse().getContentAsString())
                .path("data").path("message").asString();
        assertThat(message).isEqualTo(messageRepository.findByWalletAddress(walletAddress).orElseThrow().getMessage());

        return mvc.perform(post(ROOT + "/wallet/login")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "walletAddress", walletAddress,
                                "signature", sign(message, wallet)
                        ))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
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
