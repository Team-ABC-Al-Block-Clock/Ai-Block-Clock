package com.aiblockclock.api.v1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class WorkflowControllerTest {
    private static final String OWNER = "1";
    private static final String OTHER = "2";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    private static String chainBody(String name) {
        return """
                {
                  "name": "%s",
                  "nodes": [
                    { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" },
                    { "nodeKey": "n2", "sourceType": "NODE", "sourceId": "%s" }
                  ],
                  "edges": [
                    { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" }
                  ]
                }
                """.formatted(name, UUID.randomUUID(), UUID.randomUUID());
    }

    private String create(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workflows")
                        .header("X-Owner-Id", OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chainBody(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.nodeCount").value(2))
                .andExpect(jsonPath("$.data.edgeCount").value(1))
                .andExpect(jsonPath("$.data.revision").value(1))
                .andReturn();

        return mapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("workflowId").asText();
    }

    @Test
    @DisplayName("저장하고, 조회하고, 수정하고, 목록에서 보고, 삭제한다")
    void lifecycle() throws Exception {
        String workflowId = create("성적 분석 리포트");

        mockMvc.perform(get("/api/v1/workflows/{id}", workflowId).header("X-Owner-Id", OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("성적 분석 리포트"))
                .andExpect(jsonPath("$.data.nodes.length()").value(2))
                .andExpect(jsonPath("$.data.nodes[0].nodeKey").value("n1"))
                .andExpect(jsonPath("$.data.nodes[0].versionId").doesNotExist())
                .andExpect(jsonPath("$.data.edges[0].edgeKey").value("e1"))
                .andExpect(jsonPath("$.data.lastExecutedAt").doesNotExist());

        mockMvc.perform(put("/api/v1/workflows/{id}", workflowId)
                        .header("X-Owner-Id", OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "이름을 고쳤다",
                                  "revision": 1,
                                  "nodes": [ { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" } ],
                                  "edges": []
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("이름을 고쳤다"))
                .andExpect(jsonPath("$.data.revision").value(2))
                .andExpect(jsonPath("$.data.nodeCount").value(1));

        mockMvc.perform(get("/api/v1/workflows")
                        .header("X-Owner-Id", OWNER)
                        .param("keyword", "고쳤다"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].workflowId").value(workflowId))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        mockMvc.perform(delete("/api/v1/workflows/{id}", workflowId).header("X-Owner-Id", OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowId").value(workflowId));

        mockMvc.perform(get("/api/v1/workflows/{id}", workflowId).header("X-Owner-Id", OWNER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_002"));
    }

    @Test
    @DisplayName("주체를 밝히지 않으면 401 이다")
    void rejectsMissingOwner() throws Exception {
        mockMvc.perform(get("/api/v1/workflows"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("남의 워크플로우는 조회도 삭제도 못 한다")
    void rejectsOtherOwner() throws Exception {
        String workflowId = create("남의 것");

        mockMvc.perform(get("/api/v1/workflows/{id}", workflowId).header("X-Owner-Id", OTHER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_003"));

        mockMvc.perform(delete("/api/v1/workflows/{id}", workflowId).header("X-Owner-Id", OTHER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_003"));
    }

    @Test
    @DisplayName("낡은 회차로 수정하면 충돌로 막는다")
    void rejectsStaleRevision() throws Exception {
        String workflowId = create("회차 충돌");

        mockMvc.perform(put("/api/v1/workflows/{id}", workflowId)
                        .header("X-Owner-Id", OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "회차 충돌",
                                  "revision": 99,
                                  "nodes": [ { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" } ],
                                  "edges": []
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_016"));
    }

    @Test
    @DisplayName("순환이 있으면 저장하지 않는다")
    void rejectsCycle() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .header("X-Owner-Id", OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "순환",
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" },
                                    { "nodeKey": "n2", "sourceType": "NODE", "sourceId": "%s" }
                                  ],
                                  "edges": [
                                    { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" },
                                    { "edgeKey": "e2", "sourceNodeKey": "n2", "targetNodeKey": "n1" }
                                  ]
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_005"));
    }

    @Test
    @DisplayName("노드가 없으면 저장하지 않는다")
    void rejectsEmptyNodes() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .header("X-Owner-Id", OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "빈 캔버스", "nodes": [], "edges": [] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("에이전트 배치는 아직 열려 있지 않다")
    void rejectsAgentPlacement() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .header("X-Owner-Id", OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "에이전트 배치",
                                  "nodes": [ { "nodeKey": "n1", "sourceType": "AGENT", "sourceId": "%s" } ],
                                  "edges": []
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_015"));
    }
}
