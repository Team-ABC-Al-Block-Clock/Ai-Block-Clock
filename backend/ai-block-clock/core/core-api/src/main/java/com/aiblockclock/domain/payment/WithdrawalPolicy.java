package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

// 출금 신청이 지켜야 할 규칙이다(PAY-027). 어긋나면 무엇이 어긋났는지 화면이 그릴 값을 함께 준다.
// 금액 범위 → 처리 중인 출금 → 신청 주기 → 정산금 순으로 본다. 사용자가 금액만 고치면 되는 것부터 알려 준다.
public record WithdrawalPolicy(
        AbcAmount minimumAmount,
        AbcAmount maximumAmount,
        Duration requestInterval
) {

    public void ensureAllowed(
            AbcAmount amount,
            BigDecimal payoutBalance,
            RecentWithdrawals recent,
            Instant now
    ) {
        if (amount.value().compareTo(minimumAmount.value()) < 0
                || amount.value().compareTo(maximumAmount.value()) > 0) {
            throw new CoreException(ErrorType.WITHDRAWAL_AMOUNT_OUT_OF_RANGE, Map.of(
                    "minimumAmount", minimumAmount.value().toPlainString(),
                    "maximumAmount", maximumAmount.value().toPlainString()));
        }

        if (recent.anyInProgress()) {
            throw new CoreException(ErrorType.WITHDRAWAL_ALREADY_IN_PROGRESS);
        }

        if (recent.lastRequestedAt() != null) {
            Instant availableAt = recent.lastRequestedAt().plus(requestInterval);

            if (now.isBefore(availableAt)) {
                throw new CoreException(ErrorType.WITHDRAWAL_TOO_FREQUENT, Map.of("availableAt", availableAt));
            }
        }

        if (payoutBalance.compareTo(amount.value()) < 0) {
            throw new CoreException(ErrorType.WITHDRAWAL_PAYOUT_INSUFFICIENT,
                    Map.of("payoutAmount", payoutBalance.toPlainString()));
        }
    }
}
