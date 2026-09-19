package com.aiblockclock.domain.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "login-session")
public record LoginSessionProperties(@NotNull Duration ttl) {
    @AssertTrue(message = "로그인 세션 유효 시간은 1초 이상의 정수 초여야 합니다.")
    public boolean isTtlValid() {
        return ttl == null || (ttl.isPositive() && ttl.getNano() == 0);
    }
}
