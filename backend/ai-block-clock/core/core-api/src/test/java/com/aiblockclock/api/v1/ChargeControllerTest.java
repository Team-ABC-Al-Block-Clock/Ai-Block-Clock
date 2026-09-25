package com.aiblockclock.api.v1;

import com.aiblockclock.client.blockchain.ChainTokenTransfer;
import com.aiblockclock.client.blockchain.ChainTransaction;
import com.aiblockclock.client.blockchain.ChainTransactionStatus;
import com.aiblockclock.client.blockchain.SsafyChainClient;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 기본 설정(체인 31221 · 보관 지갑 · abcToken)을 그대로 쓴다. 체인 답만 목으로 준다
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChargeControllerTest {
    private static final String WALLET = "0x1111111111111111111111111111111111111111";
    private static final String CUSTODY = "0x64e72a170a7a36ce09806190ed02085fe23cb6b6";
    private static final String TOKEN = "0x56dc097c14836473f4ad4bd675a3d01be5e4c63c";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @MockitoBean
    private SsafyChainClient ssafyChainClient;

    private String hash;

    // 요청 저장은 따로 커밋되므로 테스트 트랜잭션이 되돌려도 남는다. 되돌린 뒤에 지운다
    @AfterTransaction
    void cleanUp() {
        if (hash != null) {
            chargeRequestRepository.findByChainIdAndTransactionHash(31221L, hash)
                    .ifPresent(chargeRequestRepository::delete);
        }
    }

    @Test
    void 이미_블록에_적힌_입금이면_요청_자리에서_적립까지_끝난다() throws Exception {
        long userId = userRepository.saveAndFlush(UserEntity.of(WALLET)).getId();
        hash = "0x" + "a".repeat(64);
        given(ssafyChainClient.readTransaction(hash)).willReturn(new ChainTransaction(
                ChainTransactionStatus.SUCCEEDED, 31221L, BigInteger.TEN, BigInteger.TEN,
                List.of(new ChainTokenTransfer(TOKEN, WALLET, CUSTODY, new BigInteger("10000000000000000000")))));

        mockMvc.perform(post("/api/v1/credits/charges").with(as(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionHash\":\"%s\",\"amount\":10}".formatted(hash)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.creditedAmount").value("10.000000"))
                .andExpect(jsonPath("$.data.failureReason").doesNotExist());

        mockMvc.perform(get("/api/v1/credits/balance").with(as(userId)))
                .andExpect(jsonPath("$.data.availableAmount").value("10.000000"));
    }

    @Test
    void 아직_블록에_적히지_않았으면_확인_중으로_받고_번호로_다시_묻는다() throws Exception {
        long userId = userRepository.saveAndFlush(UserEntity.of(WALLET)).getId();
        hash = "0x" + "b".repeat(64);
        given(ssafyChainClient.readTransaction(hash)).willReturn(new ChainTransaction(
                ChainTransactionStatus.PENDING, 31221L, null, null, List.of()));

        String body = mockMvc.perform(post("/api/v1/credits/charges").with(as(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionHash\":\"%s\",\"amount\":\"1.5\"}".formatted(hash)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.requestedAmount").value("1.500000"))
                .andReturn().getResponse().getContentAsString();
        String chargeId = body.replaceAll(".*\"chargeId\":\"(\\d+)\".*", "$1");

        mockMvc.perform(get("/api/v1/credits/charges/" + chargeId).with(as(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void 해시_형식이_틀리면_저장하지_않고_400으로_막는다() throws Exception {
        long userId = userRepository.saveAndFlush(UserEntity.of(WALLET)).getId();

        mockMvc.perform(post("/api/v1/credits/charges").with(as(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionHash\":\"0x1234\",\"amount\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 소수_7자리_금액은_400으로_막는다() throws Exception {
        long userId = userRepository.saveAndFlush(UserEntity.of(WALLET)).getId();

        mockMvc.perform(post("/api/v1/credits/charges").with(as(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionHash\":\"0x%s\",\"amount\":\"1.0000001\"}".formatted("c".repeat(64))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 보관_지갑으로_로그인했으면_403으로_막는다() throws Exception {
        long userId = userRepository.saveAndFlush(UserEntity.of(CUSTODY)).getId();

        mockMvc.perform(post("/api/v1/credits/charges").with(as(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionHash\":\"0x%s\",\"amount\":10}".formatted("d".repeat(64))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_009"));
    }

    @Test
    void 로그인하지_않으면_충전할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/credits/charges").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionHash\":\"0x%s\",\"amount\":10}".formatted("e".repeat(64))))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor as(long userId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                UserId.of(userId), null, List.of()));
    }
}
