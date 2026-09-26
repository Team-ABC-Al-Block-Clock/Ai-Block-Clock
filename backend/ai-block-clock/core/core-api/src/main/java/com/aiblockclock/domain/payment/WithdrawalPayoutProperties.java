package com.aiblockclock.domain.payment;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// 승인된 출금을 체인으로 보내는 차례의 설정이다(PAY-025 · PAY-026). 체인 · 보관 지갑 · 토큰은 충전 설정(payment.charge)을 그대로 쓴다 —
// 사용자가 넣은 그 지갑 · 그 토큰에서 내보내기 때문이다. 값이 틀리면 기동하지 않는다.
@Validated
@ConfigurationProperties(prefix = "payment.withdrawal.payout")
public record WithdrawalPayoutProperties(
        // 보관 지갑 키가 있는 서버에서만 켠다. 꺼 두면 승인된 출금은 APPROVED 로 기다린다
        boolean enabled,
        @NotNull Duration pollInterval,
        // 보낸 뒤 이만큼 지나도 체인에서 결과를 모르면 보류(ON_HOLD)로 넘긴다
        @NotNull Duration holdAfter
) {

    @AssertTrue(message = "출금 지급 간격은 0보다 커야 합니다.")
    public boolean isPollIntervalPositive() {
        return pollInterval == null || pollInterval.isPositive();
    }

    // 지급 간격보다 짧으면 보낸 뒤 처음 확인할 때 곧바로 보류가 되어, 블록에 적힐 틈도 없이 보류로 보인다
    @AssertTrue(message = "출금 보류 기준은 지급 간격보다 길어야 합니다.")
    public boolean isHoldAfterLongerThanPollInterval() {
        return pollInterval == null || holdAfter == null || holdAfter.compareTo(pollInterval) > 0;
    }
}
