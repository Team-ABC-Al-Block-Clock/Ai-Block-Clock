package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.payment.AbcBalance;

import java.math.BigDecimal;

// 실행 전 안내다. 모자라면 얼마가 모자란지 알려 충전을 안내하고, 실제로 막는 것은 실행을 접수할 때다.
// 잔액과 부족액은 0 이 정상이라 양수만 받는 AbcAmount 로 감싸지 않는다.
public record CostQuote(CostEstimate estimate, BigDecimal available, BigDecimal shortage) {

    // ledger_account.balance 컬럼과 같은 자릿수다.
    private static final int DECIMAL_PLACES = 6;

    public static CostQuote of(CostEstimate estimate, AbcBalance balance) {
        BigDecimal shortage = estimate.total().value()
                .subtract(balance.available())
                .max(BigDecimal.ZERO)
                .setScale(DECIMAL_PLACES);

        return new CostQuote(estimate, balance.available(), shortage);
    }

    public boolean executable() {
        return shortage.signum() == 0;
    }
}
