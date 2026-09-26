package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.workflow.BranchCondition;
import com.aiblockclock.domain.workflow.ConditionOperator;
import com.aiblockclock.domain.workflow.ControlNode;
import com.aiblockclock.domain.workflow.GraphEdge;
import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.node.OutputKind;
import com.aiblockclock.enums.workflow.SourceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import tools.jackson.databind.json.JsonMapper;

public record RuntimeWorkflow(String json, Map<String, List<RefFile>> refFiles) {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public RuntimeWorkflow {
        refFiles = Map.copyOf(refFiles);
    }

    public static RuntimeWorkflow of(Workflow workflow, ExecutionPlan plan) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Map<String, List<RefFile>> refFiles = new LinkedHashMap<>();
        Map<NodeKey, UUID> agents = agentsBySlot(workflow);
        Map<NodeKey, String> entries = new HashMap<>();
        Map<NodeKey, String> exits = new HashMap<>();

        for (int index = 0; index < plan.prompts().size(); index++) {
            String stepId = stepIdOf(index);
            PlannedPrompt prompt = plan.prompts().get(index);

            nodes.add(node(stepId, prompt, agents.get(prompt.placedKey())));

            if (!prompt.refFiles().isEmpty()) {
                refFiles.put(stepId, prompt.refFiles());
            }

            String previous = exits.put(prompt.placedKey(), stepId);

            if (previous == null) {
                entries.put(prompt.placedKey(), stepId);
            } else {
                edges.add(edge(previous, stepId, null));
            }
        }

        List<ControlNode> branches = workflow.getGraph().nodesInOrder().stream()
                .filter(ControlNode.class::isInstance)
                .map(ControlNode.class::cast)
                .toList();

        for (int index = 0; index < branches.size(); index++) {
            String branchId = "b" + (index + 1);
            entries.put(branches.get(index).key(), branchId);
            exits.put(branches.get(index).key(), branchId);
        }

        branches.forEach(branch -> nodes.add(branch(exits.get(branch.key()), branch.condition(), exits)));

        for (GraphEdge placed : workflow.getGraph().edgesInOrder()) {
            String from = exits.get(placed.sourceNodeKey());
            String to = entries.get(placed.targetNodeKey());

            if (from != null && to != null) {
                edges.add(edge(from, to, placed.when()));
            }
        }

        return new RuntimeWorkflow(MAPPER.writeValueAsString(Map.of(
                "id", workflow.getId().value().toString(),
                "name", workflow.getName(),
                "nodes", nodes,
                "edges", edges)), refFiles);
    }

    // 엔진은 이 번호로 단계마다 결과를 돌려준다. 끝난 뒤 값을 셀 때도 같은 번호로 찾는다.
    public static String stepIdOf(int index) {
        return "n" + (index + 1);
    }

    // 캔버스에 놓인 에이전트 자리마다 그 에이전트다. 에이전트 안의 에이전트도 바깥 자리의 에이전트로 묶는다.
    private static Map<NodeKey, UUID> agentsBySlot(Workflow workflow) {
        return workflow.getGraph().nodesInOrder().stream()
                .filter(placed -> placed.sourceType() == SourceType.AGENT)
                .collect(Collectors.toMap(GraphNode::key, placed -> placed.reference().referenceId()));
    }

    // agent_id 는 엔진이 단계마다 그대로 돌려준다. 실패한 단계가 어느 에이전트의 것인지 이것으로 안다.
    private static Map<String, Object> node(String id, PlannedPrompt prompt, UUID agent) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", "llm");
        node.put("name", prompt.name());
        node.put("owner_id", Long.toString(prompt.owner().value()));

        if (agent != null) {
            node.put("agent_id", agent.toString());
        }

        node.put("prompt", prompt.prompt().value());
        node.put("output", outputOf(prompt.output()));

        if (!prompt.refFiles().isEmpty()) {
            node.put("ref_files", prompt.refFiles().stream().map(RefFile::filename).toList());
        }

        return node;
    }

    private static Map<String, Object> outputOf(NodeOutput output) {
        return output.kind() == OutputKind.JSON
                ? Map.of("kind", "json", "sample", output.sample())
                : Map.of("kind", "text");
    }

    // 분기 조건은 캔버스 자리를 가리키고, 엔진은 단계 번호로 읽는다. 에이전트 자리는 그 에이전트의 마지막 단계다.
    private static Map<String, Object> branch(String id, BranchCondition condition, Map<NodeKey, String> exits) {
        String left = condition.left();
        String path = left.substring(left.indexOf('.') + 1);
        String source = exits.get(condition.source().orElseThrow());

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", "branch");
        node.put("condition", Map.of("left", source + "." + path, "op", opOf(condition.operator()),
                "right", condition.right()));

        return node;
    }

    private static Map<String, Object> edge(String from, String to, Boolean when) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("from", from);
        edge.put("to", to);

        if (when != null) {
            edge.put("when", when);
        }

        return edge;
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
