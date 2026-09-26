package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

// 출금 신청 정책 값이다(PAY-027). 시연처럼 값을 바꿔야 할 때 코드를 고치지 않도록 설정으로 둔다. 값이 틀리면 기동하지 않는다.
@Validated
@ConfigurationProperties(prefix = "payment.withdrawal")
public record WithdrawalProperties(
        @NotNull @Positive @Digits(integer = 32, fraction = 6) BigDecimal minimumAmount,
        @NotNull @Positive @Digits(integer = 32, fraction = 6) BigDecimal maximumAmount,
        @NotNull Duration requestInterval
) {

    @AssertTrue(message = "출금 최대 금액은 최소 금액 이상이어야 합니다.")
    public boolean isMaximumNotBelowMinimum() {
        return minimumAmount == null || maximumAmount == null || maximumAmount.compareTo(minimumAmount) >= 0;
    }

    // 0 이면 주기 없이 처리 중인 출금만 막는다
    @AssertTrue(message = "출금 신청 주기는 0 이상이어야 합니다.")
    public boolean isRequestIntervalNotNegative() {
        return requestInterval == null || !requestInterval.isNegative();
    }

    public WithdrawalPolicy policy() {
        return new WithdrawalPolicy(AbcAmount.of(minimumAmount), AbcAmount.of(maximumAmount), requestInterval);
    }
}
