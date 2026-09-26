package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.workflow.SourceType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import tools.jackson.databind.json.JsonMapper;

public record RuntimeWorkflow(String json, Map<String, List<RefFile>> refFiles) {
    private static final String STEP_SCHEMA = "common.text@1";
    private static final String RESULT_SCHEMA = "common.document@1";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public RuntimeWorkflow {
        refFiles = Map.copyOf(refFiles);
    }

    public static RuntimeWorkflow of(Workflow workflow, ExecutionPlan plan) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Map<String, List<RefFile>> refFiles = new LinkedHashMap<>();
        Map<NodeKey, UUID> agents = agentsBySlot(workflow);

        for (int index = 0; index < plan.prompts().size(); index++) {
            String nodeId = stepIdOf(index);
            PlannedPrompt prompt = plan.prompts().get(index);
            boolean last = index == plan.prompts().size() - 1;

            nodes.add(node(nodeId, prompt, agents.get(prompt.placedKey()), last));

            if (!prompt.refFiles().isEmpty()) {
                refFiles.put(nodeId, prompt.refFiles());
            }

            if (index > 0) {
                edges.add(Map.of("from", stepIdOf(index - 1), "to", nodeId));
            }
        }

        return new RuntimeWorkflow(MAPPER.writeValueAsString(Map.of(
                "id", workflow.getId().value().toString(),
                "name", workflow.getName(),
                "nodes", nodes,
                "edges", edges,
                "output", Map.of("schema_id", RESULT_SCHEMA))), refFiles);
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
    private static Map<String, Object> node(String id, PlannedPrompt prompt, UUID agent, boolean last) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", "llm");
        node.put("name", prompt.name());
        node.put("owner_id", Long.toString(prompt.owner().value()));

        if (agent != null) {
            node.put("agent_id", agent.toString());
        }

        node.put("prompt", prompt.prompt().value());
        node.put("output_schema_id", last ? RESULT_SCHEMA : STEP_SCHEMA);

        if (!prompt.refFiles().isEmpty()) {
            node.put("ref_files", prompt.refFiles().stream().map(RefFile::filename).toList());
        }

        return node;
    }
}
