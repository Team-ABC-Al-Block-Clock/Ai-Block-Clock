package com.aiblockclock.api.v1.request;

import com.aiblockclock.domain.workflow.InputField;
import com.aiblockclock.enums.workflow.InputFieldType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "실행할 때 사용자가 채울 입력 항목. 시작 노드에만 붙는다")
public record InputFieldRequest(
        @Schema(description = "채운 값을 담을 키. 영문 소문자로 시작한다", example = "brand_name")
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{0,39}$") String key,

        @Schema(description = "입력란에 보일 이름", example = "브랜드명", maxLength = 40)
        @NotBlank @Size(max = 40) String label,

        @Schema(description = "입력란의 종류")
        @NotNull InputFieldType type,

        @Schema(description = "반드시 채워야 하나. 비우면 false", example = "true", nullable = true)
        Boolean required,

        @Schema(description = "고를 수 있는 값. `CHOICE` 일 때만 보낸다",
                example = "[\"보급형\", \"중가\", \"프리미엄\"]", nullable = true)
        @Size(max = 10) List<@NotBlank @Size(max = 40) String> choices
) {
    private static final String RESERVED_KEY = "comment";

    @Schema(hidden = true)
    @AssertTrue(message = "CHOICE 는 고를 값을 하나 이상 보내야 하고, 나머지 종류는 보내지 않습니다.")
    public boolean isChoosable() {
        if (type == null) {
            return true;
        }

        return type.needsChoices()
                ? choices != null && !choices.isEmpty()
                : choices == null || choices.isEmpty();
    }

    @Schema(hidden = true)
    @AssertTrue(message = "comment 는 사용자 코멘트가 쓰는 이름이라 입력 항목의 키로 쓸 수 없습니다.")
    public boolean isNotReserved() {
        return !RESERVED_KEY.equals(key);
    }

    public InputField toField() {
        return new InputField(key, label, type, Boolean.TRUE.equals(required), choices);
    }
}
