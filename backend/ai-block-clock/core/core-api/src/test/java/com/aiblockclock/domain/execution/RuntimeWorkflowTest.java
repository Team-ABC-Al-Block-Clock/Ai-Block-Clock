package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.node.RefFileId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.BranchCondition;
import com.aiblockclock.domain.workflow.ConditionOperator;
import com.aiblockclock.domain.workflow.GraphEdge;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowFixtures;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.OutputKind;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class RuntimeWorkflowTest {
    private static final UserId REQUESTER = UserId.of(7001L);
    private static final UserId SELLER = UserId.of(7002L);
    private static final UserId INNER_SELLER = UserId.of(7003L);
    private static final AgentId AGENT = AgentId.of(UUID.fromString("5f0c7b2e-8a1d-4c3b-9e6f-2d4a8b1c7e90"));
    private static final AgentId INNER_AGENT = AgentId.of(UUID.fromString("0b6f8f7a-3c2d-4e1f-a9b8-7c6d5e4f3a21"));
    private static final String COMPETITION = "{\"competition_level\":\"high\"}";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void 캔버스의_에이전트는_agent_노드_하나로_속_그래프를_그대로_싣는다() {
        ExecutionPlan plan = ExecutionPlanner.plan(graph(
                List.of(placed("a1", agent(AGENT, graph(
                                List.of(placed("research", step("시장 조사", SELLER)),
                                        placed("strategy", step("전략 수립", SELLER))),
                                List.of(edge("research", "strategy"))))),
                        placed("summary", step("요약", REQUESTER))),
                List.of(edge("a1", "summary"))));

        JsonNode sent = sent(plan);
        JsonNode agentNode = nodeById(sent.get("nodes"), "a1");

        assertThat(sent.get("nodes")).extracting(node -> node.get("id").asText()).containsExactly("a1", "n1");
        assertThat(agentNode.get("type").asText()).isEqualTo("agent");
        assertThat(agentNode.get("agent").get("agent_id").asText()).isEqualTo(AGENT.value().toString());
        assertThat(agentNode.get("agent").get("nodes")).extracting(node -> node.get("id").asText())
                .containsExactly("n1", "n2");
        assertThat(agentNode.get("agent").get("nodes").get(0).get("owner_id").asText()).isEqualTo("7002");
        assertThat(edgesOf(agentNode.get("agent"))).containsExactly("n1>n2");
        assertThat(edgesOf(sent)).containsExactly("a1>n1");
        assertThat(nodeById(sent.get("nodes"), "n1").get("owner_id").asText()).isEqualTo("7001");
        assertThat(nodeById(sent.get("nodes"), "n1").has("agent_id")).isFalse();
    }

    @Test
    void 에이전트_안의_단계는_엔진이_펼친_이름으로_센다() {
        ExecutionPlan plan = ExecutionPlanner.plan(graph(
                List.of(placed("a1", agent(AGENT, graph(
                                List.of(placed("research", step("시장 조사", SELLER)),
                                        placed("strategy", step("전략 수립", SELLER))),
                                List.of(edge("research", "strategy"))))),
                        placed("summary", step("요약", REQUESTER))),
                List.of(edge("a1", "summary"))));

        assertThat(plan.prompts()).extracting(PlannedPrompt::stepId).containsExactly("a1.n1", "a1.n2", "n1");
        assertThat(plan.prompts()).extracting(PlannedPrompt::placedKey)
                .containsExactly(NodeKey.of("a1"), NodeKey.of("a1"), NodeKey.of("summary"));
        assertThat(plan.promptCount()).isEqualTo(3);
    }

    @Test
    void 에이전트_안의_분기와_참_거짓_갈래를_그대로_싣는다() {
        ExecutionPlan plan = ExecutionPlanner.plan(graph(
                List.of(placed("a1", agent(AGENT, graph(
                        List.of(placed("market", step("시장 조사", SELLER, json(COMPETITION))),
                                placed("br", branch("market.competition_level")),
                                placed("high", step("고급 전략", SELLER)),
                                placed("low", step("보급 전략", SELLER))),
                        List.of(edge("market", "br"), branchEdge("br", "high", true),
                                branchEdge("br", "low", false)))))),
                List.of()));

        JsonNode inside = nodeById(sent(plan).get("nodes"), "a1").get("agent");
        JsonNode branch = nodeById(inside.get("nodes"), "b1");

        assertThat(branch.get("type").asText()).isEqualTo("branch");
        assertThat(branch.get("condition").get("left").asText()).isEqualTo("n1.competition_level");
        assertThat(branch.get("condition").get("op").asText()).isEqualTo("==");
        assertThat(edgesOf(inside)).containsExactlyInAnyOrder("n1>b1", "b1>n2:true", "b1>n3:false");
        assertThat(plan.prompts()).extracting(PlannedPrompt::stepId).containsExactly("a1.n1", "a1.n2", "a1.n3");
    }

    @Test
    void 에이전트_안의_에이전트는_바깥_에이전트_안에_풀어_넣는다() {
        ExecutionPlan plan = ExecutionPlanner.plan(graph(
                List.of(placed("a1", agent(AGENT, graph(
                        List.of(placed("brief", step("요구 정리", SELLER)),
                                placed("inner", agent(INNER_AGENT, graph(
                                        List.of(placed("draft", step("초안", INNER_SELLER)),
                                                placed("polish", step("다듬기", INNER_SELLER))),
                                        List.of(edge("draft", "polish"))))),
                                placed("review", step("검토", SELLER))),
                        List.of(edge("brief", "inner"), edge("inner", "review")))))),
                List.of()));

        JsonNode inside = nodeById(sent(plan).get("nodes"), "a1").get("agent");

        assertThat(inside.get("nodes")).extracting(node -> node.get("type").asText()).containsOnly("llm");
        assertThat(inside.get("nodes")).extracting(node -> node.get("name").asText())
                .containsExactly("요구 정리", "초안", "다듬기", "검토");
        assertThat(inside.get("nodes").get(1).get("owner_id").asText()).isEqualTo("7003");
        assertThat(edgesOf(inside)).containsExactlyInAnyOrder("n1>n2", "n2>n3", "n3>n4");
    }

    @Test
    void 캔버스의_분기가_에이전트를_가리키면_엔진이_펼친_에이전트의_끝_단계를_가리킨다() {
        ExecutionPlan plan = ExecutionPlanner.plan(graph(
                List.of(placed("a1", agent(AGENT, graph(
                                List.of(placed("research", step("시장 조사", SELLER)),
                                        placed("score", step("점수", SELLER, json(COMPETITION)))),
                                List.of(edge("research", "score"))))),
                        placed("br", branch("a1.competition_level")),
                        placed("high", step("고급 전략", REQUESTER)),
                        placed("low", step("보급 전략", REQUESTER))),
                List.of(edge("a1", "br"), branchEdge("br", "high", true), branchEdge("br", "low", false))));

        JsonNode sent = sent(plan);

        assertThat(nodeById(sent.get("nodes"), "b1").get("condition").get("left").asText())
                .isEqualTo("a1.n2.competition_level");
        assertThat(edgesOf(sent)).containsExactlyInAnyOrder("a1>b1", "b1>n1:true", "b1>n2:false");
    }

    @Test
    void 끝이_여럿인_에이전트를_분기_조건으로_가리키면_보낼_수_없다() {
        UnitGraph canvas = graph(
                List.of(placed("a1", agent(AGENT, graph(
                                List.of(placed("market", step("시장 조사", SELLER, json(COMPETITION))),
                                        placed("br", branch("market.competition_level")),
                                        placed("high", step("고급 전략", SELLER, json(COMPETITION))),
                                        placed("low", step("보급 전략", SELLER, json(COMPETITION)))),
                                List.of(edge("market", "br"), branchEdge("br", "high", true),
                                        branchEdge("br", "low", false))))),
                        placed("br", branch("a1.competition_level")),
                        placed("yes", step("예", REQUESTER)),
                        placed("no", step("아니오", REQUESTER))),
                List.of(edge("a1", "br"), branchEdge("br", "yes", true), branchEdge("br", "no", false)));

        assertThatThrownBy(() -> ExecutionPlanner.plan(canvas))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.EXECUTION_BRANCH_SOURCE_AMBIGUOUS);
    }

    @Test
    void 캔버스의_분기와_참_거짓_갈래를_싣고_조건은_단계_이름으로_바꾼다() {
        ExecutionPlan plan = ExecutionPlanner.plan(graph(
                List.of(placed("market", step("시장 조사", REQUESTER, json(COMPETITION))),
                        placed("br", branch("market.competition_level")),
                        placed("high", step("고급 전략", REQUESTER)),
                        placed("low", step("보급 전략", REQUESTER))),
                List.of(edge("market", "br"), branchEdge("br", "high", true), branchEdge("br", "low", false))));

        JsonNode sent = sent(plan);
        JsonNode branch = nodeById(sent.get("nodes"), "b1");

        assertThat(branch.get("condition").get("left").asText()).isEqualTo("n1.competition_level");
        assertThat(branch.get("condition").get("right").asText()).isEqualTo("high");
        assertThat(branch.has("output")).isFalse();
        assertThat(edgesOf(sent)).containsExactlyInAnyOrder("n1>b1", "b1>n2:true", "b1>n3:false");
    }

    @Test
    void 시작_끝_노드는_싣지_않고_그_선도_빼고_잇는다() {
        ExecutionPlan plan = ExecutionPlanner.plan(graph(
                List.of(placed("start", new IoUnit()), placed("work", step("분석", REQUESTER)),
                        placed("end", new IoUnit())),
                List.of(edge("start", "work"), edge("work", "end"))));

        JsonNode sent = sent(plan);

        assertThat(sent.get("nodes")).extracting(node -> node.get("id").asText()).containsExactly("n1");
        assertThat(sent.get("edges")).isEmpty();
    }

    @Test
    void 에이전트_안_단계의_참고_자료는_엔진이_펼친_이름으로_보낸다() {
        RefFile criteria = refFile("채점 기준표.pdf");
        ExecutionPlan plan = ExecutionPlanner.plan(graph(
                List.of(placed("a1", agent(AGENT, graph(
                        List.of(placed("grade", new NodeUnit(InputSource.defaultSource(), SELLER, "채점",
                                Prompt.of("채점한다"), AiModel.defaultModel(), text(), List.of(criteria),
                                AbcAmount.of(new BigDecimal("0.0135"))))),
                        List.of())))),
                List.of()));

        RuntimeWorkflow runtime = runtimeOf(plan);

        assertThat(runtime.refFiles()).containsExactly(entry("a1.n1", List.of(criteria)));
        assertThat(nodeById(mapper.readTree(runtime.json()).get("nodes"), "a1").get("agent").get("nodes").get(0)
                .get("ref_files")).extracting(JsonNode::asText).containsExactly("채점 기준표.pdf");
    }

    @Test
    void 단계가_쓰는_모델을_실행_한_건의_모델로_최상위에_싣는다() {
        JsonNode sent = sent(ExecutionPlanner.plan(chain(step("분석", REQUESTER), step("요약", REQUESTER))));

        assertThat(sent.get("model").asText()).isEqualTo("gemini-2.5-flash");
        assertThat(sent.get("nodes").get(0).has("model")).isFalse();
    }

    @Test
    void 공급자가_고른_Pro_모델을_그대로_싣는다() {
        JsonNode sent = sent(ExecutionPlanner.plan(chain(step("분석", REQUESTER, AiModel.GEMINI_2_5_PRO))));

        assertThat(sent.get("model").asText()).isEqualTo("gemini-2.5-pro");
    }

    @Test
    void 판매자_테스트_실행도_그_노드의_모델로_돈다() {
        JsonNode sent = mapper.readTree(RuntimeWorkflow.forTest("test-1",
                testPrompt(AiModel.GEMINI_2_5_PRO, text(), List.of())).json());

        assertThat(sent.get("model").asText()).isEqualTo("gemini-2.5-pro");
        assertThat(sent.get("nodes").get(0).has("model")).isFalse();
    }

    // 계약 v2.0 은 output_schema_id · output.schema_id 를 한 버전 동안만 별칭으로 읽고 다음 버전에서 없앤다.
    @Test
    void 모든_단계는_글을_낸다고_밝히고_옛_형식_칸은_싣지_않는다() {
        Workflow workflow = WorkflowFixtures.owned(REQUESTER, GraphFixtures.chainOf(2));
        JsonNode sent = mapper.readTree(RuntimeWorkflow.of(workflow,
                ExecutionPlanner.plan(chain(step("시장 조사", REQUESTER), step("요약", REQUESTER)))).json());

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
        RefFile criteria = refFile("채점 기준표.pdf");
        RefFile image = refFile("예시.png");
        ExecutionPlan plan = ExecutionPlanner.plan(chain(new NodeUnit(InputSource.defaultSource(), REQUESTER, "채점",
                Prompt.of("채점한다"), AiModel.defaultModel(), text(), List.of(image, criteria),
                AbcAmount.of(new BigDecimal("0.0135")))));

        RuntimeWorkflow runtime = runtimeOf(plan);

        assertThat(runtime.refFiles()).containsExactly(entry("n1", List.of(criteria)));
        assertThat(mapper.readTree(runtime.json()).get("nodes").get(0).get("ref_files"))
                .extracting(JsonNode::asText)
                .containsExactly("채점 기준표.pdf");
    }

    @Test
    void 엔진이_받는_참고_자료가_하나도_없으면_ref_files_를_싣지_않는다() {
        RuntimeWorkflow runtime = RuntimeWorkflow.forTest("test-1",
                testPrompt(AiModel.defaultModel(), text(), List.of(refFile("예시.jpg"))));

        assertThat(runtime.refFiles()).isEmpty();
        assertThat(mapper.readTree(runtime.json()).get("nodes").get(0).has("ref_files")).isFalse();
    }

    @Test
    void 단계마다_저장한_출력_형식을_계약_v2_모양으로_싣는다() {
        JsonNode sent = sent(ExecutionPlanner.plan(chain(
                step("시장 조사", REQUESTER, json(COMPETITION)), step("요약", REQUESTER))));
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
                testPrompt(AiModel.defaultModel(), json(COMPETITION), List.of())).json());

        assertThat(sent.get("nodes").get(0).get("output").get("kind").asText()).isEqualTo("json");
        assertThat(sent.get("nodes").get(0).get("output").get("example").get("competition_level").asText())
                .isEqualTo("high");
    }

    private JsonNode sent(ExecutionPlan plan) {
        return mapper.readTree(runtimeOf(plan).json());
    }

    private static RuntimeWorkflow runtimeOf(ExecutionPlan plan) {
        return RuntimeWorkflow.of(WorkflowFixtures.owned(REQUESTER, GraphFixtures.chainOf(1)), plan);
    }

    private static UnitGraph chain(NodeUnit... steps) {
        List<PlacedUnit> units = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        for (int index = 0; index < steps.length; index++) {
            units.add(placed("s" + index, steps[index]));
            if (index > 0) {
                edges.add(edge("s" + (index - 1), "s" + index));
            }
        }

        return graph(units, edges);
    }

    private static UnitGraph graph(List<PlacedUnit> units, List<GraphEdge> edges) {
        return new UnitGraph(units, edges);
    }

    private static PlacedUnit placed(String key, ExecutionUnit unit) {
        return new PlacedUnit(NodeKey.of(key), unit);
    }

    private static AgentUnit agent(AgentId agent, UnitGraph inside) {
        return new AgentUnit(agent, inside);
    }

    private static ControlUnit branch(String left) {
        return new ControlUnit(new BranchCondition(left, ConditionOperator.EQ, "high"));
    }

    private static NodeUnit step(String name, UserId owner) {
        return step(name, owner, text());
    }

    private static NodeUnit step(String name, UserId owner, NodeOutput output) {
        return new NodeUnit(InputSource.defaultSource(), owner, name, Prompt.of(name + "을 한다"),
                AiModel.defaultModel(), output, List.of(), AbcAmount.of(new BigDecimal("0.018")));
    }

    private static NodeUnit step(String name, UserId owner, AiModel model) {
        return new NodeUnit(InputSource.defaultSource(), owner, name, Prompt.of(name + "을 한다"),
                model, text(), List.of(), AbcAmount.of(new BigDecimal("0.018")));
    }

    private static PlannedPrompt testPrompt(AiModel model, NodeOutput output, List<RefFile> refFiles) {
        return new PlannedPrompt("n1", NodeKey.of("n1"), "분석", REQUESTER, Prompt.of("분석한다"), model, output,
                InputSource.defaultSource(), refFiles, AbcAmount.of(new BigDecimal("0.018")));
    }

    private static GraphEdge edge(String source, String target) {
        return GraphFixtures.edge(source + "-" + target, source, target);
    }

    private static GraphEdge branchEdge(String source, String target, boolean when) {
        return GraphFixtures.branchEdge(source + "-" + target, source, target, when);
    }

    private static NodeOutput text() {
        return new NodeOutput(OutputKind.TEXT, null);
    }

    private static NodeOutput json(String example) {
        return new NodeOutput(OutputKind.JSON, example);
    }

    private static RefFile refFile(String filename) {
        return new RefFile(RefFileId.of(UUID.randomUUID()), UUID.randomUUID() + ".bin", filename, 1024);
    }

    private static JsonNode nodeById(JsonNode nodes, String id) {
        for (JsonNode node : nodes) {
            if (node.get("id").asText().equals(id)) {
                return node;
            }
        }

        throw new AssertionError("no node " + id);
    }

    private static List<String> edgesOf(JsonNode graph) {
        List<String> edges = new ArrayList<>();
        for (JsonNode edge : graph.get("edges")) {
            edges.add(edge.get("from").asText() + ">" + edge.get("to").asText()
                    + (edge.has("when") ? ":" + edge.get("when").asBoolean() : ""));
        }

        return edges;
    }
}
