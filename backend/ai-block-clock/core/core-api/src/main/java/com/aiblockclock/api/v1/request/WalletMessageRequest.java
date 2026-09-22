package com.aiblockclock.api.v1.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

public record WalletMessageRequest(
        @Schema(description = "로그인하려는 EOA 지갑 주소", example = "0x0000000000000000000000000000000000000001")
        @NotBlank
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "지갑 주소 형식이 올바르지 않습니다.")
        String walletAddress
) {
}
