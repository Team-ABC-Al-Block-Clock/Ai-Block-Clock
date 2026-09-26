package com.aiblockclock.domain.payment;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// 충전 입금을 어느 체인·지갑·토큰으로 받고, 확인을 얼마나 기다리는지다. 값이 틀리면 기동하지 않는다.
@Validated
@ConfigurationProperties(prefix = "payment.charge")
public record ChargeProperties(
        @Positive long chainId,
        @NotNull @Pattern(regexp = ADDRESS_PATTERN) String custodyAddress,
        @NotNull @Pattern(regexp = ADDRESS_PATTERN) String depositTokenContractAddress,
        @Positive long requiredConfirmations,
        @NotNull Duration pollInterval,
        @NotNull Duration maxPollInterval,
        @NotNull Duration verificationTimeout,
        boolean pollingEnabled
) {
    private static final String ADDRESS_PATTERN = "^0x[0-9a-fA-F]{40}$";

    @AssertTrue(message = "충전 재확인 간격은 0보다 커야 합니다.")
    public boolean isPollIntervalPositive() {
        return pollInterval == null || pollInterval.isPositive();
    }

    // 체인에 닿지 못할 때 간격을 늘려 가는 상한이다. 기본 간격보다 짧으면 늘릴 수가 없다
    @AssertTrue(message = "충전 재확인 최대 간격은 기본 간격 이상이어야 합니다.")
    public boolean isMaxPollIntervalNotShorter() {
        return pollInterval == null || maxPollInterval == null || maxPollInterval.compareTo(pollInterval) >= 0;
    }

    @AssertTrue(message = "충전 확인 대기 한도는 재확인 간격보다 길어야 합니다.")
    public boolean isVerificationTimeoutLonger() {
        return pollInterval == null || verificationTimeout == null || verificationTimeout.compareTo(pollInterval) > 0;
    }

    public ChargeNetworkPolicy networkPolicy() {
        return new ChargeNetworkPolicy(chainId, custodyAddress, depositTokenContractAddress, requiredConfirmations);
    }
}
