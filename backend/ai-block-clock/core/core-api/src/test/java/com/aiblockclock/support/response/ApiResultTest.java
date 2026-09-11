package com.aiblockclock.support.response;

import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResultTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void successSerializesStatusAndPayload() {
        var json = mapper.readTree(mapper.writeValueAsString(ApiResult.success(Map.of("id", 25))));

        assertEquals("SUCCESS", json.get("status").asText());
        assertEquals(25, json.get("data").get("id").asInt());
        assertTrue(json.get("error").isNull());
    }

    @Test
    void emptySuccessSerializesWithoutPayload() {
        var json = mapper.readTree(mapper.writeValueAsString(ApiResult.success()));

        assertEquals("SUCCESS", json.get("status").asText());
        assertTrue(json.get("data").isNull());
        assertTrue(json.get("error").isNull());
    }

    @Test
    void failureSerializesContractCodeAndDetails() {
        var result = ApiResult.fail(ErrorType.WORKFLOW_REVISION_CONFLICT,
                Map.of("expectedRevision", 7L, "currentRevision", 8L));
        var json = mapper.readTree(mapper.writeValueAsString(result));

        assertEquals("FAIL", json.get("status").asText());
        assertTrue(json.get("data").isNull());
        assertEquals("WORKFLOW_016", json.get("error").get("code").asText());
        assertEquals("워크플로우가 다른 요청에 의해 수정되었습니다.", json.get("error").get("message").asText());
        assertEquals(7L, json.get("error").get("data").get("expectedRevision").asLong());
        assertEquals(8L, json.get("error").get("data").get("currentRevision").asLong());
    }
}
