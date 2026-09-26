package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.InputField;
import com.aiblockclock.domain.workflow.IoNode;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ExecutionInputValidator {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public String validate(WorkflowGraph graph, String input) {
        List<InputField> fields = startFieldsOf(graph);

        if (input == null || input.isBlank()) {
            fields.stream()
                    .filter(InputField::required)
                    .findFirst()
                    .ifPresent(field -> {
                        throw invalid(field.key());
                    });

            return null;
        }

        JsonNode values = parse(input);
        Map<String, InputField> byKey = fields.stream()
                .collect(Collectors.toMap(InputField::key, Function.identity()));

        values.propertyNames().stream()
                .filter(key -> !byKey.containsKey(key))
                .findFirst()
                .ifPresent(key -> {
                    throw invalid(key);
                });

        fields.forEach(field -> ensureMatches(field, values.get(field.key())));

        return MAPPER.writeValueAsString(values);
    }

    private static List<InputField> startFieldsOf(WorkflowGraph graph) {
        return graph.getNodes().stream()
                .filter(node -> node instanceof IoNode io && io.isStart())
                .map(node -> ((IoNode) node).fields())
                .findFirst()
                .orElse(List.of());
    }

    private static JsonNode parse(String input) {
        try {
            JsonNode values = MAPPER.readTree(input);

            if (!values.isObject()) {
                throw new CoreException(ErrorType.EXECUTION_INPUT_INVALID);
            }

            return values;
        } catch (JacksonException exception) {
            throw new CoreException(ErrorType.EXECUTION_INPUT_INVALID);
        }
    }

    private static void ensureMatches(InputField field, JsonNode value) {
        if (isEmpty(value)) {
            if (field.required()) {
                throw invalid(field.key());
            }

            return;
        }

        boolean matches = switch (field.type()) {
            case TEXT, LONG_TEXT -> value.isString();
            case NUMBER -> value.isNumber();
            case BOOLEAN -> value.isBoolean();
            case CHOICE -> value.isString() && field.choices().contains(value.asString());
        };

        if (!matches) {
            throw invalid(field.key());
        }
    }

    private static boolean isEmpty(JsonNode value) {
        return value == null || value.isNull() || (value.isString() && value.asString().isBlank());
    }

    private static CoreException invalid(String key) {
        return new CoreException(ErrorType.EXECUTION_INPUT_INVALID, Map.of("key", key));
    }
}
