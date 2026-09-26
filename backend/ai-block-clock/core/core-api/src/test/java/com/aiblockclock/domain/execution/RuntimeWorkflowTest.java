package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowFixtures;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.workflow.InputSource;

import org.junit.jupiter.api.Test;

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
    void 단계가_쓰는_모델을_실행_한_건의_모델로_최상위에_싣는다() {
        Workflow workflow = WorkflowFixtures.owned(REQUESTER, GraphFixtures.chainOf(2));
        ExecutionPlan plan = new ExecutionPlan(List.of(prompt("n1", "분석", REQUESTER), prompt("n2", "요약", REQUESTER)));

        JsonNode sent = mapper.readTree(RuntimeWorkflow.of(workflow, plan).json());

        assertThat(sent.get("model").asText()).isEqualTo("gemini-2.5-flash");
        assertThat(sent.get("nodes").get(0).has("model")).isFalse();
    }

    private static PlannedPrompt prompt(String placedKey, String name, UserId owner) {
        return new PlannedPrompt(NodeKey.of(placedKey), name, owner, Prompt.of(name + "을 한다"),
                AiModel.defaultModel(), InputSource.defaultSource(), List.of());
    }
}
