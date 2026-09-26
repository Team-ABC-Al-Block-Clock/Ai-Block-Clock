package com.aiblockclock.api.v1;

import com.aiblockclock.client.ai.IndexOutcome;
import com.aiblockclock.client.ai.RecommendClient;
import com.aiblockclock.client.blockchain.BlockchainClientError;
import com.aiblockclock.client.blockchain.BlockchainClientException;
import com.aiblockclock.client.blockchain.ChainDocument;
import com.aiblockclock.client.blockchain.ChainDocumentReceipt;
import com.aiblockclock.client.blockchain.SsafyStoDocumentClient;
import com.aiblockclock.domain.agent.AgentProvenanceService;
import com.aiblockclock.domain.agent.AgentVersionId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.agent.AgentProvenanceStatus;
import com.aiblockclock.storage.agent.AgentProvenanceEntity;
import com.aiblockclock.storage.agent.AgentProvenanceRepository;
import com.aiblockclock.storage.agent.AgentRepository;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 공개 → 체인에 적기(토큰증권 API 는 목) → 조회까지 한 줄로 이어 본다. 체인에 실제로 쓰지 않는다.
// 테스트 하나가 한 트랜잭션이라, 확정 · 시도 시각을 바꾸는 UPDATE 뒤에는 영속성 컨텍스트를 비워 다시 읽는다 (registerNow).
// 운영에서는 보내기 · 확정이 각자 트랜잭션이라 이런 일이 없다
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentProvenanceControllerTest {
    private static final String REGISTRY = "0x41e2e64a79b19e9329f82d3d55be5e767a6f571d";
    private static final String TRANSACTION_HASH = "0x" + "cd".repeat(32);

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentProvenanceRepository agentProvenanceRepository;

    @Autowired
    private AgentProvenanceService agentProvenanceService;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private RecommendClient recommendClient;

    @MockitoBean
    private SsafyStoDocumentClient ssafyStoDocumentClient;

    private long owner;

    private String walletAddress;

    private UUID nodeId;

    @BeforeEach
    void 사용자와_노드를_준비한다() {
        walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        owner = userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
        nodeId = NodeDefinitionSeeder.seed(nodeRepository, owner);
        given(recommendClient.upsert(anyList())).willReturn(new IndexOutcome("upsert", 1, 1, "v1"));
        given(ssafyStoDocumentClient.documentUrl(anyString(), anyString()))
                .willAnswer(call -> URI.create("https://sto.ssafy.io/v1/tokens/" + call.getArgument(0)
                        + "/documents/" + call.getArgument(1)));
    }

    @Test
    @DisplayName("에이전트를 공개하면 그 버전의 원본 기록이 PENDING 으로 한 줄 생긴다")
    void requestsProvenanceWhenPublished() throws Exception {
        String agentId = registerAgent(createWorkflow());

        List<AgentProvenanceEntity> rows = rowsOf(agentId);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getStatus()).isEqualTo(AgentProvenanceStatus.PENDING);
            assertThat(row.getVersionNo()).isEqualTo(1);
            assertThat(row.getDocumentName()).endsWith("-v1").hasSizeLessThanOrEqualTo(31);
            assertThat(row.getTransactionHash()).isNull();
        });
    }

    @Test
    @DisplayName("적히기 전에는 PENDING 이고 거래 · 체인 값이 비어 있으며 검증되지 않았다")
    void showsPendingBeforeTheChainConfirms() throws Exception {
        String agentId = registerAgent(createWorkflow());

        mockMvc.perform(get(provenanceOf(agentId)).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentId").value(agentId))
                .andExpect(jsonPath("$.data.owner.walletAddress").value(walletAddress))
                .andExpect(jsonPath("$.data.blockchain.network").value("SSAFY"))
                .andExpect(jsonPath("$.data.blockchain.chainId").value(31221))
                .andExpect(jsonPath("$.data.blockchain.transactionStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.blockchain.transactionHash").isEmpty())
                .andExpect(jsonPath("$.data.blockchain.blockNumber").isEmpty())
                .andExpect(jsonPath("$.data.blockchain.registeredAt").isNotEmpty())
                .andExpect(jsonPath("$.data.verification.onChainOwnerAddress").isEmpty())
                .andExpect(jsonPath("$.data.verification.verified").value(false));
    }

    @Test
    @DisplayName("체인에 적히면 확정되고, 체인에서 다시 읽은 값이 DB 와 맞으면 검증된다")
    void verifiesAgainstTheChainOnceConfirmed() throws Exception {
        String agentId = registerAgent(createWorkflow());
        given(ssafyStoDocumentClient.setDocument(anyString(), anyString(), anyString()))
                .willReturn(new ChainDocumentReceipt(REGISTRY, TRANSACTION_HASH, BigInteger.valueOf(15230417)));

        assertThat(registerNow(agentId)).isTrue();

        ArgumentCaptor<String> uri = ArgumentCaptor.captor();
        ArgumentCaptor<String> hash = ArgumentCaptor.captor();
        String documentName = rowsOf(agentId).get(0).getDocumentName();
        then(ssafyStoDocumentClient).should().setDocument(eq(documentName), uri.capture(), hash.capture());
        assertThat(uri.getValue()).endsWith("?owner=" + walletAddress);
        given(ssafyStoDocumentClient.findDocument(REGISTRY, documentName))
                .willReturn(Optional.of(new ChainDocument(uri.getValue(), hash.getValue())));

        mockMvc.perform(get(provenanceOf(agentId)).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blockchain.transactionStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.blockchain.contractAddress").value(REGISTRY))
                .andExpect(jsonPath("$.data.blockchain.transactionHash").value(TRANSACTION_HASH))
                .andExpect(jsonPath("$.data.blockchain.blockNumber").value(15230417))
                .andExpect(jsonPath("$.data.blockchain.explorerUrl")
                        .value("https://sto.ssafy.io/v1/tokens/" + REGISTRY + "/documents/" + documentName))
                .andExpect(jsonPath("$.data.verification.onChainOwnerAddress").value(walletAddress))
                .andExpect(jsonPath("$.data.verification.offChainMetadataHash").value(hash.getValue()))
                .andExpect(jsonPath("$.data.verification.onChainMetadataHash").value(hash.getValue()))
                .andExpect(jsonPath("$.data.verification.ownerMatched").value(true))
                .andExpect(jsonPath("$.data.verification.metadataMatched").value(true))
                .andExpect(jsonPath("$.data.verification.verified").value(true));
    }

    @Test
    @DisplayName("체인 기록의 해시가 DB 와 다르면 불일치로 보여 준다")
    void flagsTamperedRecord() throws Exception {
        String agentId = registerAgent(createWorkflow());
        confirm(agentId);
        String documentName = rowsOf(agentId).get(0).getDocumentName();
        given(ssafyStoDocumentClient.findDocument(REGISTRY, documentName))
                .willReturn(Optional.of(new ChainDocument("abc://agents/x/versions/1?owner=" + walletAddress,
                        "0x" + "00".repeat(32))));

        mockMvc.perform(get(provenanceOf(agentId)).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verification.ownerMatched").value(true))
                .andExpect(jsonPath("$.data.verification.metadataMatched").value(false))
                .andExpect(jsonPath("$.data.verification.verified").value(false));
    }

    @Test
    @DisplayName("체인에 닿지 못하면 불일치로 보여 주지 않고 503 이다")
    void failsWhenTheChainIsUnreachable() throws Exception {
        String agentId = registerAgent(createWorkflow());
        confirm(agentId);
        willThrow(new BlockchainClientException(BlockchainClientError.REQUEST_FAILED, "down"))
                .given(ssafyStoDocumentClient).findDocument(anyString(), anyString());

        mockMvc.perform(get(provenanceOf(agentId)).with(as(owner)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("BLOCKCHAIN_004"));
    }

    @Test
    @DisplayName("체인이 받아 주지 않으면 PENDING 으로 남아 다음 차례에 다시 보낸다")
    void keepsPendingWhenTheChainRefuses() throws Exception {
        String agentId = registerAgent(createWorkflow());
        willThrow(new BlockchainClientException(BlockchainClientError.TRANSACTION_REVERTED, "reverted"))
                .given(ssafyStoDocumentClient).setDocument(anyString(), anyString(), anyString());

        assertThat(registerNow(agentId)).isFalse();

        AgentProvenanceEntity row = rowsOf(agentId).get(0);
        assertThat(row.getStatus()).isEqualTo(AgentProvenanceStatus.PENDING);
        assertThat(row.getLastAttemptedAt()).isNotNull();
        assertThat(agentProvenanceService.readPendingVersionIds(10)).contains(versionOf(agentId));
    }

    @Test
    @DisplayName("새 버전을 공개하면 그 버전의 기록이 따로 생기고, 등록 시각은 처음 공개한 때로 남는다")
    void recordsEveryPublishedVersion() throws Exception {
        String workflowId = createWorkflow();
        String agentId = registerAgent(workflowId);
        confirm(agentId);
        String firstRequestedAt = rowsOf(agentId).get(0).getRequestedAt().toString();

        republish(agentId, workflowId);

        assertThat(rowsOf(agentId)).extracting(AgentProvenanceEntity::getVersionNo).containsExactlyInAnyOrder(1, 2);
        mockMvc.perform(get(provenanceOf(agentId)).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blockchain.transactionStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.blockchain.registeredAt").value(firstRequestedAt));
    }

    @Test
    @DisplayName("에이전트는 있는데 기록이 없으면 BLOCKCHAIN_003 이다")
    void failsWhenNoRecordExists() throws Exception {
        String agentId = registerAgent(createWorkflow());
        agentProvenanceRepository.deleteAll(rowsOf(agentId));

        mockMvc.perform(get(provenanceOf(agentId)).with(as(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BLOCKCHAIN_003"));
    }

    @Test
    @DisplayName("없는 에이전트는 MARKET_001 이다 — 기록 없음과 코드가 갈린다")
    void failsWhenAgentDoesNotExist() throws Exception {
        mockMvc.perform(get(provenanceOf(UUID.randomUUID().toString())).with(as(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MARKET_001"));
    }

    private void confirm(String agentId) {
        given(ssafyStoDocumentClient.setDocument(anyString(), anyString(), anyString()))
                .willReturn(new ChainDocumentReceipt(REGISTRY, TRANSACTION_HASH, BigInteger.valueOf(15230417)));

        assertThat(registerNow(agentId)).isTrue();
    }

    private boolean registerNow(String agentId) {
        boolean registered = agentProvenanceService.register(versionOf(agentId));
        entityManager.clear();

        return registered;
    }

    private AgentVersionId versionOf(String agentId) {
        return AgentVersionId.of(agentRepository.findById(UUID.fromString(agentId)).orElseThrow()
                .getPublicVersionId());
    }

    private List<AgentProvenanceEntity> rowsOf(String agentId) {
        return agentProvenanceRepository.findAll().stream()
                .filter(row -> row.getAgentId().toString().equals(agentId))
                .toList();
    }

    private static String provenanceOf(String agentId) {
        return "/api/v1/marketplace/agents/" + agentId + "/provenance";
    }

    private String createWorkflow() throws Exception {
        MvcResult workflow = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workflowBody("성적 분석")))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readTree(workflow.getResponse().getContentAsString())
                .get("data").get("workflowId").asText();
    }

    private String registerAgent(String workflowId) throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/v1/agents")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentBody(workflowId, 1)))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readTree(registered.getResponse().getContentAsString())
                .get("data").get("agentId").asText();
    }

    // 워크플로우를 한 번 고쳐 회차를 올린 뒤 에이전트를 갱신한다. 회차가 바뀌어야 새 버전이 생긴다
    private void republish(String agentId, String workflowId) throws Exception {
        mockMvc.perform(put("/api/v1/workflows/" + workflowId)
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"revision\": 1," + workflowBody("성적 분석 v2").strip().substring(1)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/agents/" + agentId)
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentBody(workflowId, 2)))
                .andExpect(status().isOk());
    }

    private String workflowBody(String name) {
        return """
                {
                  "name": "%s",
                  "nodes": [
                    { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" },
                    { "nodeKey": "n2", "sourceType": "NODE", "sourceId": "%s" }
                  ],
                  "edges": [ { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" } ]
                }
                """.formatted(name, nodeId, nodeId);
    }

    private static String agentBody(String workflowId, long revision) {
        return """
                {
                  "workflowId": "%s", "revision": %d,
                  "name": "성적 분석 에이전트", "summary": "성적을 분석한다",
                  "description": "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다"
                }
                """.formatted(workflowId, revision);
    }

    private RequestPostProcessor as(long userId) {
        return authentication(authenticated(UserId.of(userId), null, List.of()));
    }
}
