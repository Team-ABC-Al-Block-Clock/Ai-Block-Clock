package com.aiblockclock.api.v1;

import com.aiblockclock.domain.auth.AccessTokenValidator;
import com.aiblockclock.domain.auth.RefreshTokenHasher;
import com.aiblockclock.domain.auth.RefreshTokenReader;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.auth.WalletAuthMessageRepository;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.user.UserRepository;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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
class AuthControllerIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");
    private static final String ORIGIN = "http://localhost:5173";
    private static final String ROOT = "/api/v1/auth";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final List<String> wallets = new ArrayList<>();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletAuthMessageRepository messageRepository;
    @Autowired
    private LedgerAccountRepository accountRepository;
    @Autowired
    private RefreshTokenHasher hasher;
    @Autowired
    private RefreshTokenReader tokenReader;
    @Autowired
    private AccessTokenValidator accessTokenValidator;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void 시각을_고정한다() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void 생성한_지갑의_데이터만_정리한다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (String address : wallets) {
                userRepository.findByWalletAddressIgnoreCase(address).ifPresent(user -> {
                    jdbc.update("delete from refresh_token where session_id in "
                            + "(select id from login_session where user_id = ?)", user.getId());
                    jdbc.update("delete from login_session where user_id = ?", user.getId());
                    accountRepository.findByAccountTypeAndReferenceId(
                            LedgerAccountType.ABC_BALANCE, Long.toString(user.getId())
                    ).ifPresent(accountRepository::delete);
                    userRepository.delete(user);
                });
                messageRepository.findByWalletAddress(address).ifPresent(messageRepository::delete);
            }
        });
    }

    @Test
    void 메시지_로그인_재발급_로그아웃과_보호_API를_HTTP로_연결한다() throws Exception {
        MvcResult login = login(newWallet());
        String originalAccess = accessToken(login);
        Cookie originalCookie = cookie(login);

        assertThat(originalCookie.isHttpOnly()).isTrue();
        assertThat(originalCookie.getPath()).isEqualTo(ROOT);
        assertThat(originalCookie.getMaxAge()).isEqualTo(604800);
        assertThat(originalCookie.getSecure()).isFalse();
        assertThat(login.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Lax");
        assertThat(login.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(login.getResponse().getContentAsString())
                .doesNotContain(originalCookie.getValue(), "refreshToken");
        assertThat(tokenReader.findByRefreshTokenHash(hasher.hash(originalCookie.getValue()))).isPresent();
        mvc.perform(get("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, "Bearer " + originalAccess))
                .andExpect(status().isOk());

        when(clock.instant()).thenReturn(NOW.plusSeconds(901));
        mvc.perform(get("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, "Bearer " + originalAccess))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_020"));

        MvcResult reissued = mvc.perform(post(ROOT + "/reissue").header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(originalCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
        Cookie rotated = cookie(reissued);
        assertThat(rotated.getValue()).isNotEqualTo(originalCookie.getValue());
        assertThat(rotated.getMaxAge()).isEqualTo(604800 - 901);
        assertThat(reissued.getResponse().getContentAsString()).doesNotContain(rotated.getValue(), "refreshToken");
        String newAccess = accessToken(reissued);
        assertThat(accessTokenValidator.readClaims(newAccess).sessionId())
                .isEqualTo(tokenReader.findByRefreshTokenHash(hasher.hash(rotated.getValue()))
                        .orElseThrow().sessionId());

        mvc.perform(post(ROOT + "/reissue").header(HttpHeaders.ORIGIN, ORIGIN).cookie(originalCookie))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_016"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(get("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess))
                .andExpect(status().isOk());

        MvcResult logout = mvc.perform(post(ROOT + "/logout").header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(rotated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();
        Cookie cleared = cookie(logout);
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getPath()).isEqualTo(rotated.getPath());
        assertThat(cleared.isHttpOnly()).isTrue();
        mvc.perform(get("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_014"));
        mvc.perform(post(ROOT + "/reissue").header(HttpHeaders.ORIGIN, ORIGIN).cookie(rotated))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_014"));
    }

    @Test
    void 같은_지갑의_다음_로그인은_사용자와_ABC_계정을_재사용하고_기존_세션을_종료한다() throws Exception {
        ECKeyPair wallet = newWallet();
        MvcResult first = login(wallet);
        Long userId = userRepository.findByWalletAddressIgnoreCase(address(wallet)).orElseThrow().getId();
        Long accountId = accountRepository.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_BALANCE, Long.toString(userId)).orElseThrow().getId();
        MvcResult second = login(wallet);

        assertThat(userRepository.findByWalletAddressIgnoreCase(address(wallet)).orElseThrow().getId())
                .isEqualTo(userId);
        assertThat(accountRepository.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_BALANCE, Long.toString(userId)).orElseThrow().getId()).isEqualTo(accountId);
        assertThat(jdbc.queryForObject("select count(*) from app_user where wallet_address = ?",
                Long.class, address(wallet))).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from ledger_account where account_type = ? "
                + "and reference_id = ?", Long.class, "ABC_BALANCE", Long.toString(userId))).isEqualTo(1L);
        mvc.perform(get("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(first)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_014"));
        mvc.perform(post(ROOT + "/reissue").header(HttpHeaders.ORIGIN, ORIGIN).cookie(cookie(first)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_014"));
        mvc.perform(get("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(second)))
                .andExpect(status().isOk());
    }

    @Test
    void nonce가_포함된_메시지는_만료_시각부터_로그인에_사용하지_못한다() throws Exception {
        ECKeyPair wallet = newWallet();
        String body = loginBody(wallet);
        when(clock.instant()).thenReturn(NOW.plusSeconds(300));

        mvc.perform(post(ROOT + "/wallet/login").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_009"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(userRepository.findByWalletAddressIgnoreCase(address(wallet))).isEmpty();
        assertThat(messageRepository.findByWalletAddress(address(wallet)).orElseThrow().getUsedAt()).isNull();
    }

    @Test
    void 새_메시지를_발급하면_이전_nonce의_서명은_거절하고_새_서명만_통과한다() throws Exception {
        ECKeyPair wallet = newWallet();
        String previousBody = loginBody(wallet);
        String previousNonce = messageRepository.findByWalletAddress(address(wallet)).orElseThrow().getNonce();
        String latestBody = loginBody(wallet);
        assertThat(messageRepository.findByWalletAddress(address(wallet)).orElseThrow().getNonce())
                .isNotEqualTo(previousNonce);

        mvc.perform(post(ROOT + "/wallet/login").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(previousBody))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_008"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(post(ROOT + "/wallet/login").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(latestBody))
                .andExpect(status().isOk());
    }

    @Test
    void 주소나_서명_형식이_잘못되면_DTO에서_400으로_거절한다() throws Exception {
        mvc.perform(post(ROOT + "/wallet/message").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"walletAddress\":\"wrong\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON_001"));
        mvc.perform(post(ROOT + "/wallet/login").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "walletAddress", address(newWallet()), "signature", "0x1234"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON_001"));
        mvc.perform(post(ROOT + "/wallet/message").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 깨진_JSON을_400으로_처리한다() throws Exception {
        mvc.perform(post(ROOT + "/wallet/message").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 쿠키가_없거나_형식이_잘못되면_401이며_본문의_토큰은_사용하지_않는다() throws Exception {
        for (String route : List.of("/reissue", "/logout")) {
            mvc.perform(post(ROOT + route).header(HttpHeaders.ORIGIN, ORIGIN))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_017"));
            mvc.perform(post(ROOT + route).header(HttpHeaders.ORIGIN, ORIGIN)
                            .cookie(new Cookie("refreshToken", "bad")))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_017"));
        }

        MvcResult login = login(newWallet());
        mvc.perform(post(ROOT + "/reissue").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", cookie(login).getValue()))))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void 형식은_맞아도_다른_지갑의_서명은_거절하며_메시지를_소비하지_않는다() throws Exception {
        ECKeyPair owner = newWallet();
        String body = loginBody(owner);
        String message = messageRepository.findByWalletAddress(address(owner)).orElseThrow().getMessage();
        Sign.SignatureData signed = Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), newWallet());
        HexFormat hex = HexFormat.of();
        String wrongSignature = "0x" + hex.formatHex(signed.getR())
                + hex.formatHex(signed.getS()) + hex.formatHex(signed.getV());
        mvc.perform(post(ROOT + "/wallet/login").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "walletAddress", address(owner), "signature", wrongSignature))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_008"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(messageRepository.findByWalletAddress(address(owner)).orElseThrow().getUsedAt()).isNull();
        assertThat(userRepository.findByWalletAddressIgnoreCase(address(owner))).isEmpty();
        mvc.perform(post(ROOT + "/wallet/login").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void 세션의_절대_종료_시각이면_재발급_쿠키를_발급하지_않는다() throws Exception {
        Cookie cookie = cookie(login(newWallet()));
        when(clock.instant()).thenReturn(NOW.plusSeconds(604800));
        mvc.perform(post(ROOT + "/reissue").header(HttpHeaders.ORIGIN, ORIGIN).cookie(cookie))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_015"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void 발급_받지_않은_메시지로_로그인하면_401이다() throws Exception {
        mvc.perform(post(ROOT + "/wallet/login").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "walletAddress", address(newWallet()), "signature", "0x" + "11".repeat(65)))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_012"));
    }

    @Test
    void 로그인_요청을_재사용하면_새_쿠키를_발급하지_않는다() throws Exception {
        ECKeyPair wallet = newWallet();
        String body = loginBody(wallet);
        mvc.perform(post(ROOT + "/wallet/login").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post(ROOT + "/wallet/login").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_010"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void 허용되지_않은_출처와_출처_없는_쿠키_요청은_DB를_변경하지_않는다() throws Exception {
        MvcResult login = login(newWallet());
        Cookie original = cookie(login);
        mvc.perform(post(ROOT + "/logout").header(HttpHeaders.ORIGIN, "http://evil.example")
                        .cookie(original))
                .andExpect(status().isForbidden());
        mvc.perform(post(ROOT + "/logout").cookie(original))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON_001"));
        assertThat(tokenReader.findByRefreshTokenHash(hasher.hash(original.getValue())).orElseThrow().usedAt())
                .isNull();
        mvc.perform(get("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(login)))
                .andExpect(status().isOk());
    }

    @Test
    void Origin이_없으면_허용된_Referer로_출처를_확인한다() throws Exception {
        mvc.perform(post(ROOT + "/wallet/message").header(HttpHeaders.REFERER, ORIGIN + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("walletAddress", address(newWallet())))))
                .andExpect(status().isOk());
    }

    @Test
    void FE의_쿠키_포함_CORS_요청을_허용한다() throws Exception {
        mvc.perform(options(ROOT + "/wallet/login").header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void Swagger의_공개_API와_쿠키_API와_Bearer_API를_구분한다() throws Exception {
        MvcResult docs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var root = mapper.readTree(docs.getResponse().getContentAsString());
        var paths = root.path("paths");
        assertThat(paths.path(ROOT + "/wallet/message").path("post").path("security").isArray()).isTrue();
        assertThat(paths.path(ROOT + "/wallet/message").path("post").path("security").size()).isZero();
        assertThat(paths.path(ROOT + "/wallet/login").path("post").path("security").size()).isZero();
        assertThat(paths.path(ROOT + "/reissue").path("post").path("security").get(0).has("refreshToken")).isTrue();
        assertThat(root.path("components").path("securitySchemes").path("refreshToken").path("in").asString())
                .isEqualTo("cookie");
        assertThat(paths.path(ROOT + "/wallet/message").path("post").path("responses").has("401")).isFalse();
        assertThat(paths.path(ROOT + "/wallet/login").path("post").path("responses").path("401")
                .path("description").asString()).contains("AUTH_008");
        assertThat(paths.path("/api/v1/workflows").path("get").path("responses").path("401")
                .path("description").asString()).contains("AUTH_020");
    }

    private MvcResult login(ECKeyPair wallet) throws Exception {
        return mvc.perform(post(ROOT + "/wallet/login").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody(wallet)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900)).andReturn();
    }

    private String loginBody(ECKeyPair wallet) throws Exception {
        MvcResult result = mvc.perform(post(ROOT + "/wallet/message").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("walletAddress", address(wallet)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nonce").isString()).andReturn();
        String message = mapper.readTree(result.getResponse().getContentAsString()).path("data").path("message").asString();
        assertThat(message).isEqualTo(messageRepository.findByWalletAddress(address(wallet)).orElseThrow().getMessage());
        Sign.SignatureData signed = Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), wallet);
        HexFormat hex = HexFormat.of();
        String signature = "0x" + hex.formatHex(signed.getR()) + hex.formatHex(signed.getS()) + hex.formatHex(signed.getV());
        return mapper.writeValueAsString(Map.of("walletAddress", address(wallet), "signature", signature));
    }

    private Cookie cookie(MvcResult result) {
        return result.getResponse().getCookie("refreshToken");
    }

    private String accessToken(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asString();
    }

    private ECKeyPair newWallet() {
        ECKeyPair wallet = ECKeyPair.create(new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16)
                .add(BigInteger.ONE));
        wallets.add(address(wallet));
        return wallet;
    }

    private String address(ECKeyPair wallet) {
        return "0x" + Keys.getAddress(wallet.getPublicKey());
    }
}
