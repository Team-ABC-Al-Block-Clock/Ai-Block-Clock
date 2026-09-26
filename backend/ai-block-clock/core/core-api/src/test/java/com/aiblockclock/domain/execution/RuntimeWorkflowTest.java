package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.node.RefFileId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowFixtures;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.OutputKind;
import com.aiblockclock.enums.workflow.InputSource;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

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

    @Test
    void 공급자가_고른_Pro_모델을_그대로_싣는다() {
        Workflow workflow = WorkflowFixtures.owned(REQUESTER, GraphFixtures.chainOf(1));
        ExecutionPlan plan = new ExecutionPlan(List.of(promptOf("n1", "분석", AiModel.GEMINI_2_5_PRO)));

        JsonNode sent = mapper.readTree(RuntimeWorkflow.of(workflow, plan).json());

        assertThat(sent.get("model").asText()).isEqualTo("gemini-2.5-pro");
    }

    @Test
    void 판매자_테스트_실행도_그_노드의_모델로_돈다() {
        JsonNode sent = mapper.readTree(
                RuntimeWorkflow.forTest("test-1", promptOf("n1", "분석", AiModel.GEMINI_2_5_PRO)).json());

        assertThat(sent.get("model").asText()).isEqualTo("gemini-2.5-pro");
        assertThat(sent.get("nodes").get(0).has("model")).isFalse();
    }

    // 계약 v2.0 은 output_schema_id · output.schema_id 를 한 버전 동안만 별칭으로 읽고 다음 버전에서 없앤다.
    @Test
    void 모든_단계는_글을_낸다고_밝히고_옛_형식_칸은_싣지_않는다() {
        Workflow workflow = WorkflowFixtures.owned(REQUESTER, WorkflowGraph.of(
                List.of(GraphFixtures.node("n1"), GraphFixtures.node("n2")),
                List.of(GraphFixtures.edge("e1", "n1", "n2"))));
        ExecutionPlan plan = new ExecutionPlan(List.of(
                prompt("n1", "시장 조사", REQUESTER), prompt("n2", "요약", REQUESTER)));

        JsonNode sent = mapper.readTree(RuntimeWorkflow.of(workflow, plan).json());

        assertThat(sent.get("nodes")).allSatisfy(node -> {
            assertThat(node.get("output").get("kind").asText()).isEqualTo("text");
            assertThat(node.has("output_schema_id")).isFalse();
        });
        assertThat(sent.get("output").get("title").asText()).isEqualTo(workflow.getName());
        assertThat(sent.get("output").has("schema_id")).isFalse();
    }

    // 계약 v2.0 전에 붙인 이미지는 엔진이 받지 않는다. 실으면 그 단계가 공급자 탓으로 실패한다.
    @Test
    void 엔진이_받지_않는_형식의_참고_자료는_이름도_파일도_싣지_않는다() {
        Workflow workflow = WorkflowFixtures.owned(REQUESTER, WorkflowGraph.of(
                List.of(GraphFixtures.node("n1")), List.of()));
        RefFile criteria = refFile("채점 기준표.pdf");
        RefFile image = refFile("예시.png");
        PlannedPrompt prompt = new PlannedPrompt(NodeKey.of("n1"), "채점", REQUESTER, Prompt.of("채점한다"),
                AiModel.defaultModel(), new NodeOutput(OutputKind.TEXT, null),
                InputSource.defaultSource(), List.of(image, criteria),
                AbcAmount.of(new BigDecimal("0.0135")));

        RuntimeWorkflow runtime = RuntimeWorkflow.of(workflow, new ExecutionPlan(List.of(prompt)));

        assertThat(runtime.refFiles()).containsExactly(entry("n1", List.of(criteria)));
        assertThat(mapper.readTree(runtime.json()).get("nodes").get(0).get("ref_files"))
                .extracting(JsonNode::asText)
                .containsExactly("채점 기준표.pdf");
    }

    @Test
    void 엔진이_받는_참고_자료가_하나도_없으면_ref_files_를_싣지_않는다() {
        PlannedPrompt prompt = new PlannedPrompt(NodeKey.of("n1"), "채점", REQUESTER, Prompt.of("채점한다"),
                AiModel.defaultModel(), new NodeOutput(OutputKind.TEXT, null),
                InputSource.defaultSource(), List.of(refFile("예시.jpg")),
                AbcAmount.of(new BigDecimal("0.0135")));

        RuntimeWorkflow runtime = RuntimeWorkflow.forTest("test-1", prompt);

        assertThat(runtime.refFiles()).isEmpty();
        assertThat(mapper.readTree(runtime.json()).get("nodes").get(0).has("ref_files")).isFalse();
    }

    @Test
    void 단계마다_저장한_출력_형식을_계약_v2_모양으로_싣는다() {
        Workflow workflow = WorkflowFixtures.owned(REQUESTER, GraphFixtures.chainOf(2));
        ExecutionPlan plan = new ExecutionPlan(List.of(
                prompt("n1", "시장 조사", REQUESTER, new NodeOutput(OutputKind.JSON, "{\"competition_level\":\"high\"}")),
                prompt("n2", "요약", REQUESTER)));

        JsonNode sent = mapper.readTree(RuntimeWorkflow.of(workflow, plan).json());
        JsonNode nodes = sent.get("nodes");

        assertThat(nodes.get(0).get("output").get("kind").asText()).isEqualTo("json");
        assertThat(nodes.get(0).get("output").get("example").get("competition_level").asText()).isEqualTo("high");
        assertThat(nodes.get(1).get("output").get("kind").asText()).isEqualTo("text");
        assertThat(nodes.get(1).get("output").has("example")).isFalse();
        assertThat(nodes.get(0).has("output_schema_id")).isFalse();
        assertThat(sent.get("output").has("schema_id")).isFalse();
    }

    // 판매자 테스트 실행에서 잰 원가가 노드 원가가 된다. 글로 재면 검사 · 교정이 없어 원가가 낮게 잡힌다.
    @Test
    void 판매자_테스트_실행도_저장한_출력_형식으로_돈다() {
        JsonNode sent = mapper.readTree(RuntimeWorkflow.forTest("test-1",
                prompt("n1", "시장 조사", REQUESTER, new NodeOutput(OutputKind.JSON, "{\"competition_level\":\"high\"}"))).json());

        assertThat(sent.get("nodes").get(0).get("output").get("kind").asText()).isEqualTo("json");
        assertThat(sent.get("nodes").get(0).get("output").get("example").get("competition_level").asText()).isEqualTo("high");
    }

    private static RefFile refFile(String filename) {
        return new RefFile(RefFileId.of(UUID.randomUUID()), UUID.randomUUID() + ".bin", filename, 1024);
    }

    private static PlannedPrompt prompt(String placedKey, String name, UserId owner) {
        return prompt(placedKey, name, owner, new NodeOutput(OutputKind.TEXT, null));
    }

    private static PlannedPrompt prompt(String placedKey, String name, UserId owner, NodeOutput output) {
        return new PlannedPrompt(NodeKey.of(placedKey), name, owner, Prompt.of(name + "을 한다"),
                AiModel.defaultModel(), output, InputSource.defaultSource(), List.of(),
                AbcAmount.of(new BigDecimal("0.018")));
    }

    private static PlannedPrompt promptOf(String placedKey, String name, AiModel model) {
        return new PlannedPrompt(NodeKey.of(placedKey), name, REQUESTER, Prompt.of(name + "을 한다"),
                model, new NodeOutput(OutputKind.TEXT, null), InputSource.defaultSource(), List.of(),
                AbcAmount.of(new BigDecimal("0.018")));
    }
}
