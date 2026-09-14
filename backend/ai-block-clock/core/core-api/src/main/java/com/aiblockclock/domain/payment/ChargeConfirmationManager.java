package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ChargeConfirmationManager {

    private static final String SYSTEM_ACCOUNT_REFERENCE_ID = "SYSTEM";
    private static final String CHARGE_REQUEST_REFERENCE_TYPE =
            "CHARGE_REQUEST";
    private static final String CHARGE_IDEMPOTENCY_KEY_PREFIX =
            "charge-request:";

    private final ChargeRequestReader chargeRequestReader;
    private final LedgerTransactionWriter ledgerTransactionWriter;
    private final ChargeRequestWriter chargeRequestWriter;

    @Transactional
    public ChargeRequestEntity confirm(
            Long chargeRequestId,
            BigDecimal creditedAbcAmount
    ) {
        ChargeRequestEntity chargeRequest =
                chargeRequestReader.read(chargeRequestId);

        if (chargeRequest.getStatus() != ChargeStatus.PENDING) {
            return chargeRequest;
        }

        LedgerTransaction ledgerTransaction =
                createChargeLedgerTransaction(
                        chargeRequest,
                        creditedAbcAmount
                );

        ledgerTransactionWriter.append(ledgerTransaction);

        return chargeRequestWriter.confirm(
                chargeRequest,
                creditedAbcAmount,
                Instant.now()
        );
    }

    private LedgerTransaction createChargeLedgerTransaction(
            ChargeRequestEntity chargeRequest,
            BigDecimal creditedAbcAmount
    ) {
        return new LedgerTransaction(
                LedgerEventType.CHARGE,
                CHARGE_IDEMPOTENCY_KEY_PREFIX
                        + chargeRequest.getId(),
                CHARGE_REQUEST_REFERENCE_TYPE,
                chargeRequest.getId().toString(),
                chargeRequest.getChainId(),
                chargeRequest.getTransactionHash(),
                List.of(
                        new LedgerEntry(
                                LedgerAccountType.ABC_ISSUANCE,
                                SYSTEM_ACCOUNT_REFERENCE_ID,
                                creditedAbcAmount.negate()
                        ),
                        new LedgerEntry(
                                LedgerAccountType.ABC_BALANCE,
                                chargeRequest.getUserReferenceId(),
                                creditedAbcAmount
                        )
                )
        );
    }
}
