package com.aiblockclock.api.v1.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "본인 확인 제출")
public record IdentityVerificationSubmitRequest(
        @Schema(description = "실명. 신분증에 적힌 그대로", example = "김현수")
        @NotBlank
        @Size(max = 50)
        String legalName,

        @Schema(description = "생년월일 (YYYY-MM-DD)", example = "1999-03-15")
        @NotNull
        @Past
        LocalDate birthDate
) {
}
