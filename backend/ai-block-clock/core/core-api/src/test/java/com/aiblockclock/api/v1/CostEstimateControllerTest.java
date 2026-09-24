package com.aiblockclock.api.v1;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CostEstimateControllerTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    private long requester;

    private long other;

    private String workflowId;

    // 새 노드의 원가는 3 ABC 로 고정이라 두 노드짜리 워크플로우의 견적은 6 ABC 다.
    @BeforeEach
    void 노드_두_개짜리_워크플로우를_저장해_둔다() throws Exception {
        requester = createUser();
        other = createUser();

        MvcResult saved = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(requester)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "성적 분석",
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "NODE", "nodeType": "LLM",
                                      "name": "성적 분석", "prompt": "점수를 비교한다" },
                                    { "nodeKey": "n2", "sourceType": "NODE", "nodeType": "LLM",
                                      "name": "가정통신문 작성", "prompt": "가정통신문을 쓴다" }
                                  ],
                                  "edges": [ { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" } ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        workflowId = mapper.readTree(saved.getResponse().getContentAsString())
                .get("data").get("workflowId").asText();
    }

    @Test
    void 로그인하지_않으면_견적을_볼_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/executions/cost-estimates")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf(workflowId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 워크플로우_ID가_없으면_요청을_거절한다() throws Exception {
        mockMvc.perform(post("/api/v1/executions/cost-estimates")
                        .with(as(requester)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 남의_워크플로우는_견적을_볼_수_없다() throws Exception {
        estimate(other)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_003"));
    }

    @Test
    void 없는_워크플로우는_찾을_수_없다고_알린다() throws Exception {
        mockMvc.perform(post("/api/v1/executions/cost-estimates")
                        .with(as(requester)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_002"));
    }

    @Test
    void 견적과_잔액과_부족액과_내역을_문자열_금액으로_돌려준다() throws Exception {
        charge(requester, "4.000000");

        estimate(requester)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimatedAmount").value("6.000000"))
                .andExpect(jsonPath("$.data.availableAmount").value("4.000000"))
                .andExpect(jsonPath("$.data.shortageAmount").value("2.000000"))
                .andExpect(jsonPath("$.data.executable").value(false))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].nodeKey").value("n1"))
                .andExpect(jsonPath("$.data.items[0].sourceType").value("NODE"))
                .andExpect(jsonPath("$.data.items[0].name").value("성적 분석"))
                .andExpect(jsonPath("$.data.items[0].price").value("3.000000"));
    }

    @Test
    void 잔액이_넉넉하면_실행할_수_있다고_알린다() throws Exception {
        charge(requester, "10.000000");

        estimate(requester)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortageAmount").value("0.000000"))
                .andExpect(jsonPath("$.data.executable").value(true));
    }

    private ResultActions estimate(long userId) throws Exception {
        return mockMvc.perform(post("/api/v1/executions/cost-estimates")
                .with(as(userId)).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyOf(workflowId)));
    }

    private static String bodyOf(String workflowId) {
        return "{ \"workflowId\": \"" + workflowId + "\" }";
    }

    // 잔액은 운영 코드와 같은 경로(증감 UPDATE)로만 바꾼다. 장부는 사용자를 ID 의 십진 문자열로 기억한다.
    private void charge(long userId, String amount) {
        LedgerAccountEntity account = ledgerAccountRepository.save(
                LedgerAccountEntity.of(LedgerAccountType.ABC_BALANCE, Long.toString(userId)));

        ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(amount), Instant.now());
    }

    private long createUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(walletAddress));

        return user.getId();
    }

    private static RequestPostProcessor as(long userId) {
        return authentication(authenticated(UserId.of(userId), null, List.of()));
    }
}
