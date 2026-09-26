package com.aiblockclock.domain.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "wallet-auth")
public record WalletAuthProperties(
        @NotBlank String domain,
        @NotNull URI uri,
        @Positive long chainId,
        @NotNull Duration messageTtl
) {

    @AssertTrue(message = "지갑 인증 메시지 유효 시간은 0보다 커야 합니다.")
    public boolean isMessageTtlPositive() {
        return messageTtl == null || messageTtl.isPositive();
    }

    @AssertTrue(message = "지갑 인증 체인 ID 는 지원하는 네트워크여야 합니다.")
    public boolean isChainIdSupported() {
        return WalletNetwork.findByChainId(chainId).isPresent();
    }

    public WalletNetwork network() {
        return WalletNetwork.findByChainId(chainId).orElseThrow();
    }
}
