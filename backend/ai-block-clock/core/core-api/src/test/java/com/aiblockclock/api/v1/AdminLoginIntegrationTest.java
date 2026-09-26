package com.aiblockclock.api.v1;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 관리자 권한은 실제 지갑 로그인에서 붙는다. 설정 목록에 있는 지갑과 없는 지갑으로 각각 로그인해 본다.
// 개인 키 1 과 2 의 주소는 공개된 값이라 테스트에서만 쓴다.
@Tag("integration")
@SpringBootTest(properties = "admin.wallet-addresses=0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf")
@AutoConfigureMockMvc
@Transactional
class AdminLoginIntegrationTest {
    private static final String SESSION_COOKIE = "SESSION";
    private static final ECKeyPair ADMIN_WALLET = ECKeyPair.create(BigInteger.ONE);
    private static final ECKeyPair USER_WALLET = ECKeyPair.create(BigInteger.TWO);

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    @Test
    void 관리자_목록의_지갑으로_로그인하면_관리자_API를_부를_수_있다() throws Exception {
        Cookie session = login(ADMIN_WALLET);

        mvc.perform(get("/api/v1/admin/withdrawals").cookie(session))
                .andExpect(status().isOk());
    }

    @Test
    void 목록에_없는_지갑으로_로그인하면_관리자_API가_403이다() throws Exception {
        Cookie session = login(USER_WALLET);

        mvc.perform(get("/api/v1/admin/withdrawals").cookie(session))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/workflows").cookie(session))
                .andExpect(status().isOk());
    }

    private Cookie login(ECKeyPair wallet) throws Exception {
        String walletAddress = "0x" + Keys.getAddress(wallet.getPublicKey());
        String issued = mvc.perform(post("/api/v1/auth/wallet/message").with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("walletAddress", walletAddress))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String message = mapper.readTree(issued).path("data").path("message").asString();

        return mvc.perform(post("/api/v1/auth/wallet/login").with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "walletAddress", walletAddress,
                                "signature", sign(message, wallet)))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SESSION_COOKIE);
    }

    private String sign(String message, ECKeyPair wallet) {
        Sign.SignatureData signed = Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), wallet);
        HexFormat hex = HexFormat.of();
        return "0x" + hex.formatHex(signed.getR())
                + hex.formatHex(signed.getS()) + hex.formatHex(signed.getV());
    }
}
