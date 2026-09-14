package com.aiblockclock.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ChargeConfirmationManager {

    private final ChargeRequestReader chargeRequestReader;
    private final LedgerTransactionWriter ledgerTransactionWriter;
    private final ChargeRequestWriter chargeRequestWriter;

    @Transactional
    public ChargeRequest confirm(
            Long chargeRequestId,
            BigDecimal creditedAbcAmount
    ) {
        ChargeRequest chargeRequest =
                chargeRequestReader.read(chargeRequestId);

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

        ledgerTransactionWriter.append(ledgerTransaction);

        return chargeRequestWriter.update(chargeRequest);
    }
}
