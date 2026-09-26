package com.aiblockclock.domain.payment;

import java.util.UUID;

// 화면이 옮기기 버튼을 누를 때 만드는 번호다. 같은 번호가 다시 오면 또 옮기지 않고 앞서 옮긴 결과를 돌려준다.
public record PayoutConversionRequestId(UUID value) {
    public static PayoutConversionRequestId of(UUID value) {
        return new PayoutConversionRequestId(value);
    }
}
