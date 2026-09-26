package com.aiblockclock.domain.payment;

// 확인을 한 번 시도한 결과다. chainReached 가 false 면 체인에 묻지 못해 요청이 그대로 남았다.
// 재확인하는 쪽은 이것을 보고 간격을 늘린다.
public record ChargeProcessResult(
        ChargeRequest chargeRequest,
        boolean chainReached
) {
    static ChargeProcessResult reached(ChargeRequest chargeRequest) {
        return new ChargeProcessResult(chargeRequest, true);
    }

    static ChargeProcessResult unreached(ChargeRequest chargeRequest) {
        return new ChargeProcessResult(chargeRequest, false);
    }
}
