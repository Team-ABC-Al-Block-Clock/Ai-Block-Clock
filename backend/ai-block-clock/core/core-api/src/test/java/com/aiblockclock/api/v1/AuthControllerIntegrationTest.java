package com.aiblockclock.api.v1;

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
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    private static final String SESSION_COOKIE = "SESSION";
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
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);

        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getPath()).isEqualTo("/api/v1");
        assertThat(cookie.getMaxAge()).isEqualTo(-1);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Lax");
        assertThat(result.getResponse().getContentAsString())
                .contains("2026-09-17T02:00:00Z")
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
        Cookie cookie = login(newWallet()).getResponse().getCookie(SESSION_COOKIE);

        MvcResult logout = mvc.perform(post(ROOT + "/logout")
                        .with(csrf().asHeader())
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cleared = logout.getResponse().getCookie(SESSION_COOKIE);
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
        Cookie sessionCookie = issued.getResponse().getCookie(SESSION_COOKIE);
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
                        .cookie(new Cookie(SESSION_COOKIE, "bad")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
    }

    @Test
    void 로그인하면_로그인_전_세션_ID는_더_이상_쓰이지_않는다() throws Exception {
        Cookie before = mvc.perform(get(ROOT + "/csrf"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);

        Cookie after = login(newWallet(), before).getResponse().getCookie(SESSION_COOKIE);

        assertThat(after.getValue()).isNotEqualTo(before.getValue());
        assertThat(sessionCount(before)).isZero();
        mvc.perform(get("/api/v1/workflows").cookie(before))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
        mvc.perform(get("/api/v1/workflows").cookie(after))
                .andExpect(status().isOk());
    }

    @Test
    void 로그인하면_로그인_전_CSRF_토큰도_무효가_된다() throws Exception {
        MvcResult issued = mvc.perform(get(ROOT + "/csrf")).andExpect(status().isOk()).andReturn();
        Cookie sessionCookie = issued.getResponse().getCookie(SESSION_COOKIE);
        var csrfData = mapper.readTree(issued.getResponse().getContentAsString()).path("data");
        String headerName = csrfData.path("headerName").asString();
        String token = csrfData.path("token").asString();

        ECKeyPair wallet = newWallet();
        String walletAddress = address(wallet);
        MvcResult message = mvc.perform(post(ROOT + "/wallet/message")
                        .cookie(sessionCookie)
                        .header(headerName, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("walletAddress", walletAddress))))
                .andExpect(status().isOk())
                .andReturn();
        String signable = mapper.readTree(message.getResponse().getContentAsString())
                .path("data").path("message").asString();
        MvcResult loggedIn = mvc.perform(post(ROOT + "/wallet/login")
                        .cookie(sessionCookie)
                        .header(headerName, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "walletAddress", walletAddress,
                                "signature", sign(signable, wallet)
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        mvc.perform(post(ROOT + "/wallet/message")
                        .cookie(loggedIn.getResponse().getCookie(SESSION_COOKIE))
                        .header(headerName, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("walletAddress", address(newWallet())))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 다른_브라우저에서_로그인해도_기존_세션은_유지된다() throws Exception {
        ECKeyPair wallet = newWallet();
        Cookie first = login(wallet).getResponse().getCookie(SESSION_COOKIE);
        Cookie second = login(wallet).getResponse().getCookie(SESSION_COOKIE);

        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        mvc.perform(get("/api/v1/workflows").cookie(first))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/workflows").cookie(second))
                .andExpect(status().isOk());
    }

    @Test
    void 로그인_세션은_설정한_미사용_만료_시간을_갖는다() throws Exception {
        Cookie cookie = login(newWallet()).getResponse().getCookie(SESSION_COOKIE);

        assertThat(jdbcTemplate.queryForObject(
                "select MAX_INACTIVE_INTERVAL from SPRING_SESSION where SESSION_ID = ?",
                Integer.class,
                cookie.getValue()
        )).isEqualTo(2 * 60 * 60);
    }

    @Test
    void 재발급_API는_없고_Swagger는_세션_쿠키와_CSRF_헤더를_안내한다() throws Exception {
        MvcResult docs = mvc.perform(get("/api/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var root = mapper.readTree(docs.getResponse().getContentAsString());
        var paths = root.path("paths");
        var schemes = root.path("components").path("securitySchemes");
        assertThat(paths.has(ROOT + "/reissue")).isFalse();
        assertThat(schemes.path(SESSION_COOKIE).path("in").asString()).isEqualTo("cookie");
        assertThat(schemes.path("X-CSRF-TOKEN").path("in").asString()).isEqualTo("header");
        assertThat(paths.path(ROOT + "/wallet/message").path("post").path("security").size()).isZero();
        assertThat(paths.path(ROOT + "/wallet/login").path("post").path("security").size()).isZero();
        assertThat(root.path("security").get(0).has(SESSION_COOKIE)).isTrue();
        assertThat(root.path("security").get(0).has("X-CSRF-TOKEN")).isTrue();
        assertThat(root.path("servers").get(0).path("url").asString()).isEqualTo("/");
    }

    @Test
    void Swagger_화면은_Nginx_가_넘기는_api_아래에서_로그인_없이_열린다() throws Exception {
        mvc.perform(get("/api/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void 처음_로그인한_지갑은_가입을_마치고_로그아웃_뒤_다시_로그인해도_같은_계정을_쓴다() throws Exception {
        ECKeyPair wallet = newWallet();
        Cookie firstSession = login(wallet).getResponse().getCookie(SESSION_COOKIE);

        MvcResult beforeSignup = mvc.perform(get("/api/v1/users/me").cookie(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wallet.address").value(address(wallet)))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andReturn();
        String userId = mapper.readTree(beforeSignup.getResponse().getContentAsString())
                .path("data").path("userId").asString();

        // 처음 로그인할 때 ABC 장부 계정도 함께 만들어졌다는 증거다.
        mvc.perform(get("/api/v1/credits/balance").cookie(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableAmount").value("0.000000"));

        mvc.perform(multipart("/api/v1/users/me/signup")
                        .param("nickname", "김싸피")
                        .param("termsAgreed", "true")
                        .param("recoveryNoticeConfirmed", "true")
                        .with(csrf().asHeader())
                        .cookie(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));

        mvc.perform(post(ROOT + "/logout").with(csrf().asHeader()).cookie(firstSession))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").cookie(firstSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));

        Cookie secondSession = login(wallet).getResponse().getCookie(SESSION_COOKIE);

        mvc.perform(get("/api/v1/users/me").cookie(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.nickname").value("김싸피"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));
    }

    @Test
    void 다른_지갑의_키로_서명하면_로그인할_수_없다() throws Exception {
        String walletAddress = address(newWallet());
        String message = issueMessage(walletAddress);

        mvc.perform(loginRequest(walletAddress, sign(message, newWallet())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_008"));
    }

    @Test
    void 유효_시간이_지난_메시지로는_로그인할_수_없다() throws Exception {
        ECKeyPair wallet = newWallet();
        String message = issueMessage(address(wallet));
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(6)));

        mvc.perform(loginRequest(address(wallet), sign(message, wallet)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_009"));
    }

    @Test
    void 한_번_로그인에_쓴_서명으로_다시_로그인할_수_없다() throws Exception {
        ECKeyPair wallet = newWallet();
        String signature = sign(issueMessage(address(wallet)), wallet);
        mvc.perform(loginRequest(address(wallet), signature))
                .andExpect(status().isOk());

        mvc.perform(loginRequest(address(wallet), signature))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_010"));
    }

    @Test
    void 메시지를_발급받지_않은_지갑은_로그인할_수_없다() throws Exception {
        ECKeyPair wallet = newWallet();

        mvc.perform(loginRequest(address(wallet), sign("발급받지 않은 메시지", wallet)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_012"));
    }

    @Test
    void 지갑_주소나_서명_형식이_틀리면_요청_값_오류로_거절한다() throws Exception {
        ECKeyPair wallet = newWallet();
        String signature = sign("아무 메시지", wallet);

        mvc.perform(loginRequest("0x123", signature))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        mvc.perform(loginRequest(address(wallet), "hello"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    private MvcResult login(ECKeyPair wallet) throws Exception {
        return login(wallet, null);
    }

    private MvcResult login(ECKeyPair wallet, Cookie currentSession) throws Exception {
        String walletAddress = address(wallet);
        String message = issueMessage(walletAddress, currentSession);

        MockHttpServletRequestBuilder loginRequest = loginRequest(walletAddress, sign(message, wallet));
        if (currentSession != null) {
            loginRequest.cookie(currentSession);
        }

        return mvc.perform(loginRequest)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
    }

    private String issueMessage(String walletAddress) throws Exception {
        return issueMessage(walletAddress, null);
    }

    private String issueMessage(String walletAddress, Cookie currentSession) throws Exception {
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

        return message;
    }

    private MockHttpServletRequestBuilder loginRequest(String walletAddress, String signature) throws Exception {
        return post(ROOT + "/wallet/login")
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "walletAddress", walletAddress,
                        "signature", signature
                )));
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
