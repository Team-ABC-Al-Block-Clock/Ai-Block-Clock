package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.workflow.ConditionOperator;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.node.OutputKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

// 엔진 계약 v2.0 의 워크플로우 JSON 이다. 노드마다 내는 형식(output)을 밝혀야 하고, 판매자가 노드에 저장한 형식을 그대로 싣는다.
// 판매자 테스트 실행도 같은 자리를 거친다 — json 은 엔진이 검사하고 교정하므로 토큰이 더 들어, 글로 재면 원가가 낮게 잡힌다.
// 단가 · 원가는 싣지 않는다. 엔진은 돈을 모르고 받을 값은 우리가 매긴다 (계약 8.2).
public record RuntimeWorkflow(String json, Map<String, List<RefFile>> refFiles) {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public RuntimeWorkflow {
        refFiles = Map.copyOf(refFiles);
    }

    public static RuntimeWorkflow of(Workflow workflow, ExecutionPlan plan) {
        Map<String, List<RefFile>> refFiles = new LinkedHashMap<>();

        return new RuntimeWorkflow(MAPPER.writeValueAsString(Map.of(
                "id", workflow.getId().value().toString(),
                "name", workflow.getName(),
                "model", plan.model().key(),
                "nodes", nodesOf(plan.nodes(), refFiles),
                "edges", edgesOf(plan.edges()),
                "output", Map.of("title", workflow.getName()))), refFiles);
    }

    // 판매자 테스트 실행은 노드 하나만 돈다. 실행과 같은 모양이라 엔진은 같은 규칙으로 돌리고 원가를 잰다.
    public static RuntimeWorkflow forTest(String id, PlannedPrompt prompt) {
        Map<String, List<RefFile>> refFiles = new LinkedHashMap<>();

        return new RuntimeWorkflow(MAPPER.writeValueAsString(Map.of(
                "id", id,
                "name", prompt.name(),
                "model", prompt.model().key(),
                "nodes", List.of(step(prompt.stepId(), prompt, refFiles)),
                "edges", List.of(),
                "output", Map.of("title", prompt.name()))), refFiles);
    }

    static Map<String, Object> step(String id, PlannedPrompt prompt, Map<String, List<RefFile>> refFiles) {
        List<RefFile> sent = sentRefFiles(prompt);
        if (!sent.isEmpty()) {
            refFiles.put(prompt.stepId(), sent);
        }

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", "llm");
        node.put("name", prompt.name());
        node.put("owner_id", Long.toString(prompt.owner().value()));
        node.put("prompt", prompt.prompt().value());
        node.put("output", outputOf(prompt.output()));
        if (!sent.isEmpty()) {
            node.put("ref_files", sent.stream().map(RefFile::filename).toList());
        }

        return node;
    }

    static Map<String, Object> branch(PlannedBranch branch) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", branch.id());
        node.put("type", "branch");
        node.put("condition", Map.of("left", branch.left(), "op", opOf(branch.operator()),
                "right", branch.right()));

        return node;
    }

    // 에이전트는 저장된 속 그래프를 그대로 끼워 보내고 엔진이 펼친다(계약 5.2). 펼친 단계에는 엔진이 이 agent_id 를 붙여
    // 돌려주므로, 실패한 단계가 어느 에이전트의 것인지 이것으로 안다.
    static Map<String, Object> agent(PlannedAgent agent, Map<String, List<RefFile>> refFiles) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", agent.id());
        node.put("type", "agent");
        node.put("agent", Map.of(
                "agent_id", agent.agent().value().toString(),
                "nodes", nodesOf(agent.nodes(), refFiles),
                "edges", edgesOf(agent.edges())));

        return node;
    }

    private static List<Map<String, Object>> nodesOf(List<PlannedNode> planned, Map<String, List<RefFile>> refFiles) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (PlannedNode node : planned) {
            nodes.add(node.toRuntime(refFiles));
        }

        return nodes;
    }

    private static List<Map<String, Object>> edgesOf(List<PlannedEdge> planned) {
        List<Map<String, Object>> edges = new ArrayList<>();
        for (PlannedEdge link : planned) {
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("from", link.from());
            edge.put("to", link.to());
            if (link.when() != null) {
                edge.put("when", link.when());
            }
            edges.add(edge);
        }

        return edges;
    }

    // 엔진이 받지 않는 형식(계약 v2.0 전에 붙인 이미지)은 싣지 않는다. 실으면 엔진이 그 단계를 공급자 탓으로 실패시키고,
    // 이미지는 어차피 엔진이 읽지 않아 빼도 잃는 것이 없다. 파일 이름 목록과 파일 파트가 같은 순서여야 한다.
    private static List<RefFile> sentRefFiles(PlannedPrompt prompt) {
        return prompt.refFiles().stream()
                .filter(RefFile::acceptedByRuntime)
                .toList();
    }

    private static Map<String, Object> outputOf(NodeOutput output) {
        return output.kind() == OutputKind.JSON
                ? Map.of("kind", "json", "example", MAPPER.readTree(output.example()))
                : Map.of("kind", "text");
    }

    private static String opOf(ConditionOperator operator) {
        return switch (operator) {
            case EQ -> "==";
            case NE -> "!=";
            case GT -> ">";
            case GE -> ">=";
            case LT -> "<";
            case LE -> "<=";
            case IN -> "in";
        };
    }
}
