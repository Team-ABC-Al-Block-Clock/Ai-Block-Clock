package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;

// 옮기기 한 번의 결과다. 같은 요청 번호로 다시 오면 convertedAmount 는 처음 옮긴 금액이다.
public record PayoutConversion(AbcAmount convertedAmount, AbcBalance balance) {
}
