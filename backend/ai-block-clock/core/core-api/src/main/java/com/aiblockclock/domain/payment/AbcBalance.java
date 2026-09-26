package com.aiblockclock.domain.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 사용자가 보는 ABC 잔액. available 은 지금 쓸 수 있는 금액, reserved 는 실행 시작 때 선차감되어 결과를 기다리는 금액이다.
// payout 은 공급자로서 받은 정산금이다. 실행에 쓸 수 없고 출금만 할 수 있어 available 과 따로 둔다.
// 잔액은 0 이 정상이라 양수만 허용하는 AbcAmount 로 감싸지 않는다.
public record AbcBalance(BigDecimal available, BigDecimal reserved, BigDecimal payout) {

    // ledger_account.balance 컬럼과 같은 자릿수다.
    private static final int DECIMAL_PLACES = 6;

    public AbcBalance {
        available = available.setScale(DECIMAL_PLACES, RoundingMode.UNNECESSARY);
        reserved = reserved.setScale(DECIMAL_PLACES, RoundingMode.UNNECESSARY);
        payout = payout.setScale(DECIMAL_PLACES, RoundingMode.UNNECESSARY);
    }

    public static AbcBalance of(BigDecimal available, BigDecimal reserved, BigDecimal payout) {
        return new AbcBalance(available, reserved, payout);
    }

    public static AbcBalance empty() {
        return new AbcBalance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
