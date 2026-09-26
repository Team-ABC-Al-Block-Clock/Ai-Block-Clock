package com.aiblockclock.api.v1.request;

import com.aiblockclock.enums.node.OutputKind;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import tools.jackson.databind.JsonNode;

@Schema(description = """
        이 노드가 결과를 내는 형식. `TEXT` 는 자유 텍스트, `JSON` 은 판매자가 적은 예시(`example`)와 같은 모양의 JSON 이다.
        분기 조건은 `JSON` 노드의 칸만 읽는다.""")
public record NodeOutputRequest(
        @Schema(description = "형식 종류", example = "JSON")
        @NotNull OutputKind kind,

        @Schema(description = """
                결과 JSON 의 예시. `JSON` 일 때만 보낸다. 키가 하나 이상인 객체여야 하고, 값을 `null` 로 둔 키는 선택 항목이다""",
                example = "{ \"competition_level\": \"high\", \"competitors\": [ { \"name\": \"…\" } ] }",
                nullable = true)
        JsonNode example
) {
    @Schema(hidden = true)
    @AssertTrue(message = "JSON 은 키가 하나 이상인 예시 객체를 보내고, TEXT 는 예시를 보내지 않아야 합니다.")
    public boolean isExampleMatchingKind() {
        if (kind == OutputKind.JSON) {
            return example != null && example.isObject() && !example.isEmpty();
        }

        return example == null || example.isNull();
    }

    public String exampleJson() {
        return kind == OutputKind.JSON ? example.toString() : null;
    }
}
