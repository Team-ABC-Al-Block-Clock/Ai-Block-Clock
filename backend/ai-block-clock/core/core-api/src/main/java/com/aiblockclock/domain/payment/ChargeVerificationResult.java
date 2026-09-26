package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatusReason;

public record ChargeVerificationResult(
        ChargeVerificationOutcome outcome,
        ChargeStatusReason reason
) {

    public static ChargeVerificationResult waiting() {
        return new ChargeVerificationResult(
                ChargeVerificationOutcome.WAITING,
                null
        );
    }

    public static ChargeVerificationResult unreachable() {
        return new ChargeVerificationResult(
                ChargeVerificationOutcome.UNREACHABLE,
                null
        );
    }

    public static ChargeVerificationResult verified() {
        return new ChargeVerificationResult(
                ChargeVerificationOutcome.VERIFIED,
                null
        );
    }

    public static ChargeVerificationResult rejected(
            ChargeStatusReason reason
    ) {
        return new ChargeVerificationResult(
                ChargeVerificationOutcome.REJECTED,
                reason
        );
    }
}
