package com.aiblockclock.api.v1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class NodeControllerTest {
    private static final String OWNER = "31";
    private static final String OTHER = "32";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    private static String body(String name, String prompt) {
        return """
                { "name": "%s", "prompt": "%s" }
                """.formatted(name, prompt);
    }

    private String create(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/nodes")
                        .header("X-Owner-Id", OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, "당신은 학원 성적 데이터 분석가입니다.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.baseCost").value("3.000000"))
                .andExpect(jsonPath("$.data.currency").value("ABC"))
                .andReturn();

        return mapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("nodeId").asText();
    }

    @Test
    @DisplayName("만들고, 조회하고, 수정하고, 목록에서 보고, 삭제한다")
    void lifecycle() throws Exception {
        String nodeId = create("성적 분석");

        mockMvc.perform(get("/api/v1/nodes/{id}", nodeId).header("X-Owner-Id", OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("성적 분석"))
                .andExpect(jsonPath("$.data.prompt").value("당신은 학원 성적 데이터 분석가입니다."))
                .andExpect(jsonPath("$.data.model").value("GEMINI_2_5_FLASH"));

        mockMvc.perform(put("/api/v1/nodes/{id}", nodeId)
                        .header("X-Owner-Id", OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("가정통신문 작성", "당신은 교육 심리 전문가입니다.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("가정통신문 작성"));

        mockMvc.perform(get("/api/v1/nodes").header("X-Owner-Id", OWNER).param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].nodeId").value(nodeId))
                .andExpect(jsonPath("$.data.content[0].prompt").doesNotExist());

        mockMvc.perform(delete("/api/v1/nodes/{id}", nodeId).header("X-Owner-Id", OWNER))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/nodes/{id}", nodeId).header("X-Owner-Id", OWNER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NODE_001"));
    }

    @Test
    @DisplayName("프롬프트가 비어 있으면 만들 수 없다")
    void rejectsBlankPrompt() throws Exception {
        mockMvc.perform(post("/api/v1/nodes")
                        .header("X-Owner-Id", OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("빈 프롬프트", "   ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("남의 노드는 조회도 수정도 못 한다")
    void rejectsOtherOwner() throws Exception {
        String nodeId = create("남의 노드");

        mockMvc.perform(get("/api/v1/nodes/{id}", nodeId).header("X-Owner-Id", OTHER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NODE_002"));

        mockMvc.perform(put("/api/v1/nodes/{id}", nodeId)
                        .header("X-Owner-Id", OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("탈취", "남의 것을 고친다.")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/nodes/{id}", nodeId).header("X-Owner-Id", OWNER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("워크플로우에 놓인 노드는 삭제할 수 없다")
    void rejectsDeleteWhilePlaced() throws Exception {
        String nodeId = create("배치된 노드");

        MvcResult saved = mockMvc.perform(post("/api/v1/workflows")
                        .header("X-Owner-Id", OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "노드 하나짜리",
                                  "nodes": [ { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" } ],
                                  "edges": []
                                }
                                """.formatted(nodeId)))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(delete("/api/v1/nodes/{id}", nodeId).header("X-Owner-Id", OWNER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NODE_004"));

        String workflowId = mapper.readTree(saved.getResponse().getContentAsString())
                .get("data").get("workflowId").asText();
        mockMvc.perform(delete("/api/v1/workflows/{id}", workflowId).header("X-Owner-Id", OWNER))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/nodes/{id}", nodeId).header("X-Owner-Id", OWNER))
                .andExpect(status().isOk());
    }
}
