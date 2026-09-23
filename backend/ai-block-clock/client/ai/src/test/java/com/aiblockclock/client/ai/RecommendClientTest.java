package com.aiblockclock.client.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RecommendClientTest {
    private static final String BASE_URL = "http://ai-block-clock-recommend:8100";

    private MockRestServiceServer server;
    private RecommendClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RecommendClient(builder.build());
    }

    @Test
    @DisplayName("색인에 넣을 때 추천 엔진이 읽는 이름으로 보낸다")
    void sendsIndexRecordsInTheEnginesFieldNames() {
        server.expect(requestTo(BASE_URL + "/v1/index/agents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.op").value("upsert"))
                .andExpect(jsonPath("$.agents[0].agent_id").value("33567ab2-e08b-461b-a0f2-51a857df5ec7"))
                .andExpect(jsonPath("$.agents[0].success_rate").value(1.0))
                .andExpect(jsonPath("$.agents[0].is_public").value(true))
                .andExpect(jsonPath("$.agents[0].tier").value(4))
                .andRespond(withSuccess("""
                        { "op": "upsert", "indexed": 1, "total": 21,
                          "model_version": "abc-agent-retriever-v1" }
                        """, MediaType.APPLICATION_JSON));

        IndexOutcome outcome = client.upsert(List.of(new IndexedAgent(
                "33567ab2-e08b-461b-a0f2-51a857df5ec7", "성적 분석 에이전트", "성적을 분석한다",
                List.of(), "", "9301", 4, 1.0, true)));

        assertThat(outcome.indexed()).isEqualTo(1);
        assertThat(outcome.total()).isEqualTo(21);
        assertThat(outcome.modelVersion()).isEqualTo("abc-agent-retriever-v1");
        server.verify();
    }

    @Test
    @DisplayName("추천은 우리가 읽지 않는 칸이 섞여 와도 깨지지 않는다")
    void readsRecommendationsBesideFieldsWeDoNotUse() {
        server.expect(requestTo(BASE_URL + "/v1/recommend"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.query").value("성적표로 리포트를 쓰고 싶어"))
                .andExpect(jsonPath("$.top_k").value(8))
                .andRespond(withSuccess("""
                        { "recommendations": [
                            { "agent_id": "ag_seed_01", "name": "성적 데이터 분석기",
                              "owner_id": "u_2001", "score": 0.8123,
                              "breakdown": { "semantic": 0.84, "trust": 0.73, "lexical": 12.41, "schema_fit": null },
                              "tier": 2, "success_rate": 0.96, "category": "교육",
                              "input_schema_id": "edu.roster@1", "output_schema_id": "edu.analysis@1",
                              "reason": "학생별 점수 표를 받아 비교합니다" } ],
                          "model_version": "abc-agent-retriever-v1", "latency_ms": 31 }
                        """, MediaType.APPLICATION_JSON));

        List<RecommendedAgent> found = client.recommend("성적표로 리포트를 쓰고 싶어", 8);

        assertThat(found).singleElement()
                .extracting(RecommendedAgent::agentId, RecommendedAgent::name, RecommendedAgent::reason)
                .containsExactly("ag_seed_01", "성적 데이터 분석기", "학생별 점수 표를 받아 비교합니다");
        server.verify();
    }

    @Test
    @DisplayName("추천 엔진이 죽어 있으면 요청 실패로 끊는다")
    void failsWhenTheEngineIsDown() {
        server.expect(requestTo(BASE_URL + "/v1/recommend"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.recommend("성적표", 8))
                .isInstanceOf(RecommendClientException.class)
                .extracting(e -> ((RecommendClientException) e).getError())
                .isEqualTo(RecommendClientError.REQUEST_FAILED);
        server.verify();
    }
}
