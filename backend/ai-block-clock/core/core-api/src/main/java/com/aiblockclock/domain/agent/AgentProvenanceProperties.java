package com.aiblockclock.domain.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// 원본 기록을 체인에 보내는 주기 작업과, 조회 응답에 싣는 체인 정보다. 값이 틀리면 기동하지 않는다.
// 보내기(registration-enabled)를 꺼 둬도 버전을 공개하면 PENDING 기록은 쌓인다. 켜면 쌓인 것부터 보낸다.
@Validated
@ConfigurationProperties(prefix = "agent-provenance")
public record AgentProvenanceProperties(
        boolean registrationEnabled,
        @NotNull Duration registrationInterval,
        @Positive int registrationBatchSize,
        @Positive long chainId,
        @NotBlank String networkName
) {
}
