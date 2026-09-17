package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.auth.WalletAuthMessage;
import io.swagger.v3.oas.annotations.media.Schema;

public record WalletMessageResponse(
        @Schema(description = "지갑에 그대로 전달해 서명받을 메시지 원문") String message,
        @Schema(description = "메시지에 포함된 일회용 난수") String nonce
) {

    public static WalletMessageResponse from(WalletAuthMessage authMessage) {
        return new WalletMessageResponse(authMessage.message(), authMessage.nonce());
    }
}
