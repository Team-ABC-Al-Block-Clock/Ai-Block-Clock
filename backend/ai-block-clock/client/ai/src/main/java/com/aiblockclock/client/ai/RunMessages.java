package com.aiblockclock.client.ai;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// 실행 메시지를 브로커의 모양(ai/docs/integration-guide.md §4.2 ~ §4.4 · §4.6)으로 바꾸고 되읽는다.
// 본문은 UTF-8 JSON 이고 칸 이름은 snake_case 다.
// 읽지 못하는 본문(JSON 이 아니거나 꼭 있어야 할 칸이 빠졌다)은 AiClientException(MALFORMED_RESPONSE) 이다.
// 소수는 BigDecimal 로 읽는다. snapshot 원문을 다시 쓸 때 달러 원가의 자릿수가 바뀌지 않는다.
public final class RunMessages {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();
    private static final String SUBMIT_RUN = "SubmitRun";
    private static final String CANCEL_RUN = "CancelRun";
    private static final String CANCEL_ID_PREFIX = "cancel:";

    private RunMessages() {
    }

    // message_id 는 실행 번호다. 런타임은 이것으로 같은 실행의 두 번째 배달을 거른다.
    public static Message toMessage(SubmitRun run) {
        return messageOf(bodyOf(run), SUBMIT_RUN, run.executionId());
    }

    public static Message toMessage(CancelRun cancel) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("execution_id", cancel.executionId());
        body.put("requested_at", cancel.requestedAt().toString());

        return messageOf(body, CANCEL_RUN, CANCEL_ID_PREFIX + cancel.executionId());
    }

    // 보낼 본문 그대로다. 접수할 때 outbox 에 적어 두고, 디스패처가 readSubmit 으로 되읽어 보낸다.
    public static String toJson(SubmitRun run) {
        return MAPPER.writeValueAsString(bodyOf(run));
    }

    // snapshotJson 은 snapshot 을 다시 쓴 것이다. RunStatus 가 읽지 않는 칸(노드 출력 등)까지 남는다.
    public static RunEvent readEvent(byte[] body) {
        JsonNode event = treeOf(body);
        JsonNode snapshot = valueOf(event, "snapshot");

        try {
            return new RunEvent(
                    requiredText(event, "execution_id"),
                    RunMode.from(textOf(event, "mode")),
                    requiredInt(event, "attempt"),
                    requiredInt(event, "seq"),
                    RunEventType.from(requiredText(event, "type")),
                    instantOf(event, "occurred_at"),
                    snapshot == null ? null : MAPPER.treeToValue(snapshot, RunStatus.class),
                    snapshot == null ? null : MAPPER.writeValueAsString(snapshot),
                    valueOf(event, "artifact"),
                    resultFilesOf(event));
        } catch (JacksonException | DateTimeParseException exception) {
            throw malformed("run event", exception);
        }
    }

    // outbox 에 적어 둔 본문과 abc.runs.dead 로 돌아온 본문을 읽는다. 런타임이 덧붙인 칸은 무시한다.
    public static SubmitRun readSubmit(byte[] body) {
        JsonNode submit = treeOf(body);

        try {
            return new SubmitRun(
                    requiredText(submit, "execution_id"),
                    RunMode.from(textOf(submit, "mode")),
                    valueOf(submit, "workflow"),
                    valueOf(submit, "input"),
                    textOf(submit, "comment"),
                    submit.path("mock").asBoolean(false),
                    filesOf(submit),
                    refFilesOf(submit),
                    instantOf(submit, "submitted_at"));
        } catch (DateTimeParseException exception) {
            throw malformed("submit run", exception);
        }
    }

    // 입력 · 코멘트가 없어도 칸은 남기고 값을 null 로 싣는다. 파일 · 참고 자료가 없으면 [] · {} 다.
    private static ObjectNode bodyOf(SubmitRun run) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("execution_id", run.executionId());
        body.put("mode", run.mode().wireName());
        body.set("workflow", run.workflow());
        body.set("input", run.input());
        body.put("comment", run.comment());
        body.put("mock", run.mock());

        ArrayNode files = body.putArray("files");
        run.files().forEach(files::add);

        ObjectNode refFiles = body.putObject("ref_files");

        for (Map.Entry<String, List<String>> step : run.refFiles().entrySet()) {
            ArrayNode keys = refFiles.putArray(step.getKey());
            step.getValue().forEach(keys::add);
        }

        body.put("submitted_at", run.submittedAt().toString());

        return body;
    }

    private static Message messageOf(JsonNode body, String type, String messageId) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setType(type);
        properties.setMessageId(messageId);

        return new Message(MAPPER.writeValueAsBytes(body), properties);
    }

    private static JsonNode treeOf(byte[] body) {
        try {
            return MAPPER.readTree(body);
        } catch (JacksonException exception) {
            throw malformed("message body", exception);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw malformed("message without " + field, null);
        }

        return value.stringValue();
    }

    private static int requiredInt(JsonNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || !value.isIntegralNumber()) {
            throw malformed("message without " + field, null);
        }

        return value.intValue();
    }

    private static JsonNode valueOf(JsonNode node, String field) {
        JsonNode value = node.get(field);

        return value == null || value.isNull() ? null : value;
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode value = valueOf(node, field);

        return value == null ? null : value.asString();
    }

    private static Instant instantOf(JsonNode node, String field) {
        String value = textOf(node, field);

        return value == null ? null : Instant.parse(value);
    }

    private static List<String> filesOf(JsonNode submit) {
        List<String> files = new ArrayList<>();
        submit.path("files").values().forEach(file -> files.add(file.asString()));

        return files;
    }

    private static Map<String, List<String>> refFilesOf(JsonNode submit) {
        Map<String, List<String>> refFiles = new LinkedHashMap<>();

        for (Map.Entry<String, JsonNode> step : submit.path("ref_files").properties()) {
            refFiles.put(step.getKey(), step.getValue().values().stream().map(JsonNode::asString).toList());
        }

        return refFiles;
    }

    private static Map<String, String> resultFilesOf(JsonNode event) {
        JsonNode files = valueOf(event, "result_files");

        if (files == null) {
            return null;
        }

        Map<String, String> resultFiles = new LinkedHashMap<>();
        files.properties().forEach(file -> resultFiles.put(file.getKey(), file.getValue().asString()));

        return resultFiles;
    }

    private static AiClientException malformed(String what, Throwable cause) {
        return new AiClientException(AiClientError.MALFORMED_RESPONSE, what + " is not readable", cause);
    }
}
