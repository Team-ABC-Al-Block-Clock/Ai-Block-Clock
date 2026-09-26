package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ChargeConfirmationManager {

    private final LedgerTransactionWriter ledgerTransactionWriter;
    private final ChargeRequestWriter chargeRequestWriter;

    @Transactional
    public ChargeRequest confirm(
            ChargeRequest chargeRequest,
            AbcAmount creditedAbcAmount
    ) {
        if (!chargeRequest.isPending()) {
            return chargeRequest;
        }

        chargeRequest.confirm(
                creditedAbcAmount,
                Instant.now()
        );

        LedgerTransaction ledgerTransaction =
                LedgerTransaction.forCharge(
                        chargeRequest,
                        creditedAbcAmount
                );

        ChargeRequest confirmedChargeRequest =
                chargeRequestWriter.updateFromPending(
                        chargeRequest
                );

        ledgerTransactionWriter.append(ledgerTransaction);

        return confirmedChargeRequest;
    }
}
