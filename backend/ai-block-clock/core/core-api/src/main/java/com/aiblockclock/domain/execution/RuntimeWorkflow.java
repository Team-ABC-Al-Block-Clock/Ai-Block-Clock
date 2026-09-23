package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.Workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

public final class RuntimeWorkflow {
    private static final String STEP_SCHEMA = "common.text@1";
    private static final String RESULT_SCHEMA = "common.document@1";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private RuntimeWorkflow() {
    }

    public static String toJson(Workflow workflow, ExecutionPlan plan) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        for (int index = 0; index < plan.prompts().size(); index++) {
            boolean last = index == plan.prompts().size() - 1;
            nodes.add(node("n" + (index + 1), plan.prompts().get(index), last));

            if (index > 0) {
                edges.add(Map.of("from", "n" + index, "to", "n" + (index + 1)));
            }
        }

        return MAPPER.writeValueAsString(Map.of(
                "id", workflow.getId().value().toString(),
                "name", workflow.getName(),
                "nodes", nodes,
                "edges", edges,
                "output", Map.of("schema_id", RESULT_SCHEMA)));
    }

    private static Map<String, Object> node(String id, PlannedPrompt prompt, boolean last) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", "llm");
        node.put("name", prompt.name());
        node.put("prompt", prompt.prompt().value());
        node.put("output_schema_id", last ? RESULT_SCHEMA : STEP_SCHEMA);

        return node;
    }
}
