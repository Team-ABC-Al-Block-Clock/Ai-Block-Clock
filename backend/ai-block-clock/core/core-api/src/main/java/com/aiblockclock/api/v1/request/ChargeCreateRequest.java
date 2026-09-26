package com.aiblockclock.api.v1.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ChargeCreateRequest(
        @Schema(description = "MetaMask 가 abcToken 을 보관 지갑으로 보낸 뒤 돌려준 거래 해시",
                example = "0x66c02aecb4cee86cd41e000bf73ef8e24a909052874714598c46f753df011441")
        @NotBlank
        @Pattern(regexp = "^0x[0-9a-fA-F]{64}$", message = "거래 해시 형식이 올바르지 않습니다.")
        String transactionHash,

        // 금액 칸(DECIMAL(38, 6))에 담기는 범위다. 체인 금액과 다르면 확인 단계에서 실패로 닫힌다
        @Schema(description = "보낸 abcToken 개수 (= 충전할 ABC). 소수 6자리까지", example = "10")
        @NotNull
        @Positive
        @Digits(integer = 32, fraction = 6, message = "충전 수량은 소수 6자리까지입니다.")
        BigDecimal amount
) {
}
