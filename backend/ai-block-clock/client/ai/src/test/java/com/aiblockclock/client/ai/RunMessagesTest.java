package com.aiblockclock.client.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 메시지의 모양은 런타임(Python)과 나눠 갖는 계약이다 (contract 2 ~ 4 · 6장). 칸 이름 하나가 달라도 상대가 읽지 못한다.
class RunMessagesTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String EXECUTION_ID = "6f1c2a9e-0000-4000-8000-000000000001";
    private static final Instant SUBMITTED_AT = Instant.parse("2026-10-16T05:00:00Z");
    private static final String WORKFLOW = """
            {"id":"w1","name":"성적 분석","model":"gemini-2.5-flash",
             "nodes":[{"id":"n1","type":"llm","name":"성적 분석","owner_id":"7","prompt":"점수를 비교한다",
                       "output":{"kind":"text"},"ref_files":["기준표.pdf"]}],
             "edges":[],"output":{"title":"성적 분석"}}""";

    @Test
    void SubmitRun_은_JSON_본문에_계약의_메시지_속성을_달고_나간다() {
        MessageProperties properties = RunMessages.toMessage(submit()).getMessageProperties();

        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getType()).isEqualTo("SubmitRun");
        assertThat(properties.getMessageId()).isEqualTo(EXECUTION_ID);
    }

    @Test
    void SubmitRun_본문의_칸_이름은_snake_case_다() {
        JsonNode body = bodyOf(RunMessages.toMessage(submit()));

        assertThat(body.propertyNames()).containsExactlyInAnyOrder("execution_id", "mode", "workflow", "input",
                "comment", "mock", "files", "ref_files", "submitted_at");
    }

    @Test
    void SubmitRun_본문에_실행_번호_mode_코멘트_mock_시각을_싣는다() {
        JsonNode body = bodyOf(RunMessages.toMessage(submit()));

        assertThat(body.get("execution_id").asString()).isEqualTo(EXECUTION_ID);
        assertThat(body.get("mode").asString()).isEqualTo("run");
        assertThat(body.get("comment").asString()).isEqualTo("2학기 점수를 비교해 주세요");
        assertThat(body.get("mock").isBoolean()).isTrue();
        assertThat(body.get("mock").booleanValue()).isTrue();
        assertThat(body.get("submitted_at").asString()).isEqualTo("2026-10-16T05:00:00Z");
    }

    @Test
    void 워크플로우와_입력은_문자열이_아니라_JSON_객체로_싣는다() {
        JsonNode body = bodyOf(RunMessages.toMessage(submit()));

        assertThat(body.get("workflow").isObject()).isTrue();
        assertThat(body.get("workflow")).isEqualTo(MAPPER.readTree(WORKFLOW));
        assertThat(body.get("input").isObject()).isTrue();
        assertThat(body.get("input")).isEqualTo(MAPPER.readTree("{\"brand_name\":\"하루\"}"));
    }

    @Test
    void 파일과_참고_자료는_file_store_기준_상대_경로로_싣는다() {
        JsonNode body = bodyOf(RunMessages.toMessage(submit()));

        assertThat(body.get("files")).isEqualTo(MAPPER.readTree("[\"executions/" + EXECUTION_ID + "/input.xlsx\"]"));
        assertThat(body.get("ref_files"))
                .isEqualTo(MAPPER.readTree("{\"n1\":[\"nodes/n-1/r-1.pdf\",\"nodes/n-1/r-2.md\"]}"));
    }

    @Test
    void 입력과_코멘트가_없으면_null_파일이_없으면_빈_목록_참고_자료가_없으면_빈_객체로_싣는다() {
        SubmitRun bare = new SubmitRun(EXECUTION_ID, RunMode.TEST, MAPPER.readTree(WORKFLOW), null, null, false,
                List.of(), Map.of(), SUBMITTED_AT);

        JsonNode body = bodyOf(RunMessages.toMessage(bare));

        assertThat(body.get("mode").asString()).isEqualTo("test");
        assertThat(body.has("input")).isTrue();
        assertThat(body.get("input").isNull()).isTrue();
        assertThat(body.has("comment")).isTrue();
        assertThat(body.get("comment").isNull()).isTrue();
        assertThat(body.get("mock").booleanValue()).isFalse();
        assertThat(body.get("files").isArray()).isTrue();
        assertThat(body.get("files")).isEmpty();
        assertThat(body.get("ref_files").isObject()).isTrue();
        assertThat(body.get("ref_files")).isEmpty();
    }

    @Test
    void CancelRun_은_취소_번호를_message_id_로_달고_실행_번호와_요청_시각을_싣는다() {
        Message message = RunMessages.toMessage(
                new CancelRun(EXECUTION_ID, Instant.parse("2026-10-16T05:10:00Z")));
        MessageProperties properties = message.getMessageProperties();
        JsonNode body = bodyOf(message);

        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getType()).isEqualTo("CancelRun");
        assertThat(properties.getMessageId()).isEqualTo("cancel:" + EXECUTION_ID);
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("execution_id", "requested_at");
        assertThat(body.get("execution_id").asString()).isEqualTo(EXECUTION_ID);
        assertThat(body.get("requested_at").asString()).isEqualTo("2026-10-16T05:10:00Z");
    }

    @Test
    void RunEvent_의_칸을_읽는다() {
        RunEvent event = RunMessages.readEvent(bytes(progressEvent()));

        assertThat(event.executionId()).isEqualTo(EXECUTION_ID);
        assertThat(event.mode()).isEqualTo(RunMode.RUN);
        assertThat(event.attempt()).isEqualTo(1);
        assertThat(event.seq()).isEqualTo(3);
        assertThat(event.type()).isEqualTo(RunEventType.PROGRESS);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-10-16T05:00:03Z"));
        assertThat(event.artifact()).isNull();
        assertThat(event.resultFiles()).isNull();
    }

    @Test
    void snapshot_은_엔진_조회_응답과_같은_모양이라_RunStatus_로_읽는다() {
        RunStatus snapshot = RunMessages.readEvent(bytes(progressEvent())).snapshot();

        assertThat(snapshot.status()).isEqualTo("running");
        assertThat(snapshot.progress().done()).isEqualTo(1);
        assertThat(snapshot.progress().total()).isEqualTo(2);
        assertThat(snapshot.progress().currentNodeName()).isEqualTo("가정통신문 작성");
        assertThat(snapshot.nodes()).extracting(RunStatus.Node::id).containsExactly("n1", "n2");
        assertThat(snapshot.nodes().getFirst().costUsd()).isEqualByComparingTo(new BigDecimal("0.0021"));
    }

    // 정산 근거로 원문을 적는다. RunStatus 가 읽지 않는 노드 출력까지 남아야 한다.
    @Test
    void snapshot_원문은_RunStatus_가_읽지_않는_칸까지_그대로_보존한다() {
        RunEvent event = RunMessages.readEvent(bytes(progressEvent()));

        assertThat(MAPPER.readTree(event.snapshotJson())).isEqualTo(progressEvent().get("snapshot"));
        assertThat(event.snapshotJson()).contains("output_payload").contains("runtime_only_field");
    }

    @Test
    void 모르는_칸이_섞여_와도_읽는다() {
        ObjectNode event = progressEvent();
        event.put("trace_id", "abc");
        event.putObject("unknown_block").put("x", 1);

        assertThat(RunMessages.readEvent(bytes(event)).seq()).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({"accepted,ACCEPTED", "progress,PROGRESS", "succeeded,SUCCEEDED", "failed,FAILED",
            "rejected,REJECTED"})
    void type_은_계약의_다섯_값으로_읽는다(String wire, RunEventType expected) {
        ObjectNode event = progressEvent();
        event.put("type", wire);

        assertThat(RunMessages.readEvent(bytes(event)).type()).isEqualTo(expected);
    }

    @Test
    void 노드_테스트의_이벤트는_mode_test_로_읽는다() {
        ObjectNode event = progressEvent();
        event.put("mode", "test");

        assertThat(RunMessages.readEvent(bytes(event)).mode()).isEqualTo(RunMode.TEST);
    }

    @Test
    void succeeded_에_실린_결과물_봉투와_결과_파일_경로를_읽는다() {
        ObjectNode event = progressEvent();
        event.put("type", "succeeded");
        event.set("artifact", MAPPER.readTree("""
                {"a2a_version":"2.0","node_id":"n2","schema_id":"text","payload":{"text":"평균이 올랐습니다"}}"""));
        event.set("result_files", MAPPER.readTree(
                "{\"json\":\"" + EXECUTION_ID + "/artifact.json\",\"pdf\":\"" + EXECUTION_ID + "/report.pdf\"}"));

        RunEvent read = RunMessages.readEvent(bytes(event));

        assertThat(read.artifact().get("payload").get("text").asString()).isEqualTo("평균이 올랐습니다");
        assertThat(read.resultFiles()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "json", EXECUTION_ID + "/artifact.json", "pdf", EXECUTION_ID + "/report.pdf"));
    }

    @Test
    void JSON_이_아닌_이벤트는_형식_오류다() {
        assertThatThrownBy(() -> RunMessages.readEvent("<html>502</html>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(AiClientError.MALFORMED_RESPONSE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"execution_id", "type", "attempt", "seq"})
    void 꼭_있어야_할_칸이_빠진_이벤트는_형식_오류다(String missing) {
        ObjectNode event = progressEvent();
        event.remove(missing);

        assertThatThrownBy(() -> RunMessages.readEvent(bytes(event)))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(AiClientError.MALFORMED_RESPONSE));
    }

    @Test
    void dead_letter_로_돌아온_SubmitRun_본문을_다시_읽는다() {
        byte[] body = """
                {"execution_id":"6f1c2a9e-0000-4000-8000-000000000001","mode":"test","workflow":{"id":"w1","nodes":[]},
                 "input":null,"comment":null,"mock":true,"files":[],"ref_files":{},
                 "submitted_at":"2026-10-16T05:00:00Z","added_by_runtime":1}""".getBytes(StandardCharsets.UTF_8);

        SubmitRun read = RunMessages.readSubmit(body);

        assertThat(read.executionId()).isEqualTo(EXECUTION_ID);
        assertThat(read.mode()).isEqualTo(RunMode.TEST);
        assertThat(read.mock()).isTrue();
        assertThat(read.submittedAt()).isEqualTo(SUBMITTED_AT);
    }

    @Test
    void 보낸_SubmitRun_을_다시_읽으면_같은_내용이다() {
        SubmitRun read = RunMessages.readSubmit(RunMessages.toMessage(submit()).getBody());

        assertThat(read).isEqualTo(submit());
    }

    @Test
    void SubmitRun_으로_읽히지_않는_본문은_형식_오류다() {
        assertThatThrownBy(() -> RunMessages.readSubmit("not json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(AiClientError.MALFORMED_RESPONSE));
        assertThatThrownBy(() -> RunMessages.readSubmit("{\"mode\":\"run\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(AiClientError.MALFORMED_RESPONSE));
    }

    // 접수할 때 outbox 에 적는 본문이다. 디스패처가 이것을 되읽어 보내므로 보낼 본문과 같아야 한다.
    @Test
    void outbox_에_적는_SubmitRun_본문은_보내는_메시지의_본문과_같다() {
        assertThat(MAPPER.readTree(RunMessages.toJson(submit()))).isEqualTo(bodyOf(RunMessages.toMessage(submit())));
    }

    @Test
    void outbox_에_적은_SubmitRun_본문을_다시_읽으면_같은_내용이다() {
        byte[] stored = RunMessages.toJson(submit()).getBytes(StandardCharsets.UTF_8);

        assertThat(RunMessages.readSubmit(stored)).isEqualTo(submit());
    }

    // 모르는 type 이면 실행을 어느 상태로 둘지 정할 수 없다.
    @Test
    void 계약에_없는_type_의_이벤트는_형식_오류다() {
        ObjectNode event = progressEvent();
        event.put("type", "paused");

        assertThatThrownBy(() -> RunMessages.readEvent(bytes(event)))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(AiClientError.MALFORMED_RESPONSE));
    }

    // 모르는 mode 면 실행과 노드 테스트 어느 쪽으로 보낼지 정할 수 없다.
    @Test
    void 계약에_없는_mode_의_이벤트는_형식_오류다() {
        ObjectNode event = progressEvent();
        event.put("mode", "batch");

        assertThatThrownBy(() -> RunMessages.readEvent(bytes(event)))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(AiClientError.MALFORMED_RESPONSE));
    }

    @Test
    void 계약에_없는_mode_의_SubmitRun_본문은_형식_오류다() {
        byte[] body = """
                {"execution_id":"6f1c2a9e-0000-4000-8000-000000000001","mode":"batch","workflow":{"id":"w1"},
                 "input":null,"comment":null,"mock":false,"files":[],"ref_files":{},
                 "submitted_at":"2026-10-16T05:00:00Z"}""".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> RunMessages.readSubmit(body))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(AiClientError.MALFORMED_RESPONSE));
    }

    private static SubmitRun submit() {
        return new SubmitRun(EXECUTION_ID, RunMode.RUN, MAPPER.readTree(WORKFLOW),
                MAPPER.readTree("{\"brand_name\":\"하루\"}"), "2학기 점수를 비교해 주세요", true,
                List.of("executions/" + EXECUTION_ID + "/input.xlsx"),
                Map.of("n1", List.of("nodes/n-1/r-1.pdf", "nodes/n-1/r-2.md")), SUBMITTED_AT);
    }

    private static ObjectNode progressEvent() {
        return (ObjectNode) MAPPER.readTree("""
                {"execution_id":"6f1c2a9e-0000-4000-8000-000000000001","mode":"run","attempt":1,"seq":3,
                 "type":"progress","occurred_at":"2026-10-16T05:00:03Z",
                 "snapshot":{"run_id":"6f1c2a9e-0000-4000-8000-000000000001","status":"running",
                             "progress":{"done":1,"total":2,"percent":50,"current_node_id":"n2",
                                         "current_node_name":"가정통신문 작성"},
                             "nodes":[{"id":"n1","name":"성적 분석","type":"llm","status":"succeeded",
                                       "cost_usd":0.0021,"output_payload":"{\\"text\\":\\"요약\\"}"},
                                      {"id":"n2","name":"가정통신문 작성","type":"llm","status":"running"}],
                             "runtime_only_field":{"queue_wait_ms":120}},
                 "artifact":null,"result_files":null}""");
    }

    private static JsonNode bodyOf(Message message) {
        return MAPPER.readTree(message.getBody());
    }

    private static byte[] bytes(JsonNode node) {
        return MAPPER.writeValueAsBytes(node);
    }
}
