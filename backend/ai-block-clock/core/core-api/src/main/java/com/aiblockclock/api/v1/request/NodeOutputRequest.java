package com.aiblockclock.api.v1.request;

import com.aiblockclock.enums.node.OutputKind;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = """
        이 노드가 결과를 내는 형식. `TEXT` 는 자유 텍스트, `JSON` 은 견본(`sample`)의 칸을 채운 JSON 이다.
        분기 조건은 `JSON` 노드의 칸만 읽는다.""")
public record NodeOutputRequest(
        @Schema(description = "형식 종류", example = "JSON")
        @NotNull OutputKind kind,

        @Schema(description = "견본 ID. `JSON` 일 때만 보낸다", example = "biz.market_research@1", maxLength = 64,
                nullable = true)
        @Size(max = 64) String sample
) {
    @Schema(hidden = true)
    @AssertTrue(message = "JSON 은 견본 ID 를 보내고, TEXT 는 보내지 않아야 합니다.")
    public boolean isSampleMatchingKind() {
        if (kind == OutputKind.JSON) {
            return sample != null && !sample.isBlank();
        }

        return sample == null;
    }
}
