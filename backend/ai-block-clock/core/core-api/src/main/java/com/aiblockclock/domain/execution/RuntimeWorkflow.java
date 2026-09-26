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

// 엔진 계약 v2.0 의 워크플로우 JSON 이다. 노드마다 내는 형식(output)을 밝혀야 하고, 판매자가 고른 형식을 아직 저장하지 않아
// 모든 단계가 글(text)을 낸다. 글 출력은 형식 검사 · 교정이 없어 어떤 프롬프트든 그대로 돈다.
// 단가 · 원가는 싣지 않는다. 엔진은 돈을 모르고 받을 값은 우리가 매긴다 (계약 8.2).
public record RuntimeWorkflow(String json, Map<String, List<RefFile>> refFiles) {
    private static final Map<String, String> TEXT_OUTPUT = Map.of("kind", "text");

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

            nodes.add(node(nodeId, prompt, agents.get(prompt.placedKey())));

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
                "output", Map.of("title", workflow.getName()))), refFiles);
    }

    // 판매자 테스트 실행은 노드 하나만 돈다. 실행과 같은 모양이라 엔진은 같은 규칙으로 돌리고 원가를 잰다.
    public static RuntimeWorkflow forTest(String id, PlannedPrompt prompt) {
        String nodeId = stepIdOf(0);
        Map<String, List<RefFile>> refFiles = prompt.refFiles().isEmpty()
                ? Map.of()
                : Map.of(nodeId, prompt.refFiles());

        return new RuntimeWorkflow(MAPPER.writeValueAsString(Map.of(
                "id", id,
                "name", prompt.name(),
                "nodes", List.of(node(nodeId, prompt, null)),
                "edges", List.of(),
                "output", Map.of("title", prompt.name()))), refFiles);
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
        node.put("output", TEXT_OUTPUT);

        if (!prompt.refFiles().isEmpty()) {
            node.put("ref_files", prompt.refFiles().stream().map(RefFile::filename).toList());
        }

        return node;
    }
}
