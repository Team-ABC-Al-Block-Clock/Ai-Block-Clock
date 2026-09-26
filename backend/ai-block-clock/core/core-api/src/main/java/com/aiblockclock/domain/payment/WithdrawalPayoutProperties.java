package com.aiblockclock.domain.payment;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// 승인된 출금을 체인으로 보내는 차례의 설정이다(PAY-025). 체인 · 보관 지갑 · 토큰은 충전 설정(payment.charge)을 그대로 쓴다 —
// 사용자가 넣은 그 지갑 · 그 토큰에서 내보내기 때문이다. 값이 틀리면 기동하지 않는다.
@Validated
@ConfigurationProperties(prefix = "payment.withdrawal.payout")
public record WithdrawalPayoutProperties(
        // 보관 지갑 키가 있는 서버에서만 켠다. 꺼 두면 승인된 출금은 APPROVED 로 기다린다
        boolean enabled,
        @NotNull Duration pollInterval
) {

    @AssertTrue(message = "출금 지급 간격은 0보다 커야 합니다.")
    public boolean isPollIntervalPositive() {
        return pollInterval == null || pollInterval.isPositive();
    }
}
