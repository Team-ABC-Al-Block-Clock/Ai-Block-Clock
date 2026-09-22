package com.aiblockclock.api.v1.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

public record WalletLoginRequest(
        @Schema(description = "서명한 EOA 지갑 주소")
        @NotBlank
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "지갑 주소 형식이 올바르지 않습니다.")
        String walletAddress,
        @Schema(description = "서버가 발급한 메시지에 대한 65바이트 personal_sign 서명")
        @NotBlank
        @Pattern(regexp = "^0x[0-9a-fA-F]{130}$", message = "지갑 서명 형식이 올바르지 않습니다.")
        String signature
) {
}
