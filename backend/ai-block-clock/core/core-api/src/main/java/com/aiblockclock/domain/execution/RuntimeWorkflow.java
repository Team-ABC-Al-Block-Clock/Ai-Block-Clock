package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.workflow.Workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        for (int index = 0; index < plan.prompts().size(); index++) {
            String nodeId = "n" + (index + 1);
            PlannedPrompt prompt = plan.prompts().get(index);
            boolean last = index == plan.prompts().size() - 1;

            nodes.add(node(nodeId, prompt, last));

            if (!prompt.refFiles().isEmpty()) {
                refFiles.put(nodeId, prompt.refFiles());
            }

            if (index > 0) {
                edges.add(Map.of("from", "n" + index, "to", nodeId));
            }
        }

        return new RuntimeWorkflow(MAPPER.writeValueAsString(Map.of(
                "id", workflow.getId().value().toString(),
                "name", workflow.getName(),
                "nodes", nodes,
                "edges", edges,
                "output", Map.of("schema_id", RESULT_SCHEMA))), refFiles);
    }

    private static Map<String, Object> node(String id, PlannedPrompt prompt, boolean last) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", "llm");
        node.put("name", prompt.name());
        node.put("owner_id", Long.toString(prompt.owner().value()));
        node.put("prompt", prompt.prompt().value());
        node.put("output_schema_id", last ? RESULT_SCHEMA : STEP_SCHEMA);

        if (!prompt.refFiles().isEmpty()) {
            node.put("ref_files", prompt.refFiles().stream().map(RefFile::filename).toList());
        }

        return node;
    }
}
