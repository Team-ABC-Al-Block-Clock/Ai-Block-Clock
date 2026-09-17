package com.aiblockclock.domain.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;

@Validated
@ConfigurationProperties(prefix = "auth-token")
public record AuthTokenProperties(
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration sessionTtl,
        @NotBlank(message = "JWT 서명 비밀키를 설정해야 합니다.") String secretKeyBase64
) {
    private static final int MINIMUM_SECRET_KEY_BYTES = 32;

    @AssertTrue(message = "Access Token 유효 시간은 1초 이상의 정수 초여야 합니다.")
    public boolean isAccessTokenTtlValid() {
        return accessTokenTtl == null || (accessTokenTtl.isPositive() && accessTokenTtl.getNano() == 0);
    }

    @AssertTrue(message = "로그인 세션 유효 시간은 1초 이상의 정수 초여야 합니다.")
    public boolean isSessionTtlValid() {
        return sessionTtl == null || (sessionTtl.isPositive() && sessionTtl.getNano() == 0);
    }

    @AssertTrue(message = "JWT 서명 비밀키는 32바이트 이상의 값을 Base64로 인코딩해야 합니다.")
    public boolean isSecretKeyValid() {
        if (secretKeyBase64 == null || secretKeyBase64.isBlank()) {
            return true;
        }

        try {
            return Base64.getDecoder().decode(secretKeyBase64).length >= MINIMUM_SECRET_KEY_BYTES;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "AuthTokenProperties[issuer=%s, accessTokenTtl=%s, sessionTtl=%s, secretKeyBase64=***]"
                .formatted(issuer, accessTokenTtl, sessionTtl);
    }
}
