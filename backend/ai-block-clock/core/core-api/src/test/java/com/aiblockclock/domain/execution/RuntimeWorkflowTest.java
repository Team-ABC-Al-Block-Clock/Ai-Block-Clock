package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowFixtures;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.enums.node.OutputKind;
import com.aiblockclock.enums.workflow.InputSource;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeWorkflowTest {
    private static final UserId REQUESTER = UserId.of(7001L);
    private static final UserId SELLER = UserId.of(7002L);
    private static final UUID AGENT = UUID.fromString("5f0c7b2e-8a1d-4c3b-9e6f-2d4a8b1c7e90");

    private final JsonMapper mapper = JsonMapper.builder().build();

    // 캔버스: a1(남의 에이전트, 속에 단계 둘) → n2(내 노드). 엔진에는 단계 셋이 평평하게 간다.
    @Test
    void 에이전트_안의_단계에만_그_에이전트를_실어_보낸다() {
        Workflow workflow = WorkflowFixtures.owned(REQUESTER, WorkflowGraph.of(
                List.of(GraphFixtures.agent("a1", AGENT, UUID.randomUUID()), GraphFixtures.node("n2")),
                List.of(GraphFixtures.edge("e1", "a1", "n2"))));
        ExecutionPlan plan = new ExecutionPlan(List.of(
                prompt("a1", "시장 조사", SELLER), prompt("a1", "전략 수립", SELLER), prompt("n2", "요약", REQUESTER)));

        JsonNode nodes = mapper.readTree(RuntimeWorkflow.of(workflow, plan).json()).get("nodes");

        assertThat(nodes.get(0).get("agent_id").asText()).isEqualTo(AGENT.toString());
        assertThat(nodes.get(1).get("agent_id").asText()).isEqualTo(AGENT.toString());
        assertThat(nodes.get(2).has("agent_id")).isFalse();
        assertThat(nodes.get(0).get("owner_id").asText()).isEqualTo("7002");
        assertThat(nodes.get(2).get("owner_id").asText()).isEqualTo("7001");
    }

    @Test
    void 단계마다_저장한_출력_형식을_계약_v2_모양으로_싣는다() {
        Workflow workflow = WorkflowFixtures.owned(REQUESTER, GraphFixtures.chainOf(2));
        ExecutionPlan plan = new ExecutionPlan(List.of(
                prompt("n1", "시장 조사", REQUESTER, new NodeOutput(OutputKind.JSON, "biz.market_research@1")),
                prompt("n2", "요약", REQUESTER)));

        JsonNode sent = mapper.readTree(RuntimeWorkflow.of(workflow, plan).json());
        JsonNode nodes = sent.get("nodes");

        assertThat(nodes.get(0).get("output").get("kind").asText()).isEqualTo("json");
        assertThat(nodes.get(0).get("output").get("sample").asText()).isEqualTo("biz.market_research@1");
        assertThat(nodes.get(1).get("output").get("kind").asText()).isEqualTo("text");
        assertThat(nodes.get(1).get("output").has("sample")).isFalse();
        assertThat(nodes.get(0).has("output_schema_id")).isFalse();
        assertThat(sent.has("output")).isFalse();
    }

    // 캔버스: market → br(market.competition_level == high) → 참 high · 거짓 low
    @Test
    void 분기_노드와_참_거짓_갈래를_싣고_조건은_단계_번호로_바꾼다() {
        Workflow workflow = WorkflowFixtures.owned(REQUESTER, WorkflowGraph.of(
                List.of(GraphFixtures.node("market"), GraphFixtures.branch("br", "market.competition_level"),
                        GraphFixtures.node("high"), GraphFixtures.node("low")),
                List.of(GraphFixtures.edge("e1", "market", "br"),
                        GraphFixtures.branchEdge("e2", "br", "high", true),
                        GraphFixtures.branchEdge("e3", "br", "low", false))));
        ExecutionPlan plan = new ExecutionPlan(List.of(
                prompt("market", "시장 조사", REQUESTER, new NodeOutput(OutputKind.JSON, "biz.market_research@1")),
                prompt("high", "고급 전략", REQUESTER), prompt("low", "보급 전략", REQUESTER)));

        JsonNode sent = mapper.readTree(RuntimeWorkflow.of(workflow, plan).json());
        JsonNode branch = nodeById(sent.get("nodes"), "b1");

        assertThat(branch.get("type").asText()).isEqualTo("branch");
        assertThat(branch.get("condition").get("left").asText()).isEqualTo("n1.competition_level");
        assertThat(branch.get("condition").get("op").asText()).isEqualTo("==");
        assertThat(branch.get("condition").get("right").asText()).isEqualTo("high");
        assertThat(branch.has("output")).isFalse();
        assertThat(edges(sent)).containsExactlyInAnyOrder("n1>b1", "b1>n2:true", "b1>n3:false");
    }

    // 캔버스: a1(에이전트, 속에 단계 둘) → n2. 에이전트 속 단계는 한 줄로 잇고, 캔버스 선은 에이전트의 끝에서 나간다.
    @Test
    void 에이전트_속_단계는_한_줄로_잇고_캔버스_선은_에이전트의_마지막_단계에서_나간다() {
        Workflow workflow = WorkflowFixtures.owned(REQUESTER, WorkflowGraph.of(
                List.of(GraphFixtures.agent("a1", AGENT, UUID.randomUUID()), GraphFixtures.node("n2")),
                List.of(GraphFixtures.edge("e1", "a1", "n2"))));
        ExecutionPlan plan = new ExecutionPlan(List.of(
                prompt("a1", "시장 조사", SELLER), prompt("a1", "전략 수립", SELLER), prompt("n2", "요약", REQUESTER)));

        assertThat(edges(mapper.readTree(RuntimeWorkflow.of(workflow, plan).json())))
                .containsExactlyInAnyOrder("n1>n2", "n2>n3");
    }

    private static JsonNode nodeById(JsonNode nodes, String id) {
        for (JsonNode node : nodes) {
            if (node.get("id").asText().equals(id)) {
                return node;
            }
        }

        throw new AssertionError("no node " + id);
    }

    private static List<String> edges(JsonNode sent) {
        List<String> edges = new ArrayList<>();
        for (JsonNode edge : sent.get("edges")) {
            edges.add(edge.get("from").asText() + ">" + edge.get("to").asText()
                    + (edge.has("when") ? ":" + edge.get("when").asBoolean() : ""));
        }

        return edges;
    }

    private static PlannedPrompt prompt(String placedKey, String name, UserId owner) {
        return prompt(placedKey, name, owner, new NodeOutput(OutputKind.TEXT, null));
    }

    private static PlannedPrompt prompt(String placedKey, String name, UserId owner, NodeOutput output) {
        return new PlannedPrompt(NodeKey.of(placedKey), name, owner, Prompt.of(name + "을 한다"), output,
                InputSource.defaultSource(), List.of());
    }
}
