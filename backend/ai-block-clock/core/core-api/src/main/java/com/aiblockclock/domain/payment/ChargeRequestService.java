package com.aiblockclock.domain.payment;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChargeRequestService {

    private final ChargeRequestReader chargeRequestReader;
    private final ChargeRequestWriter chargeRequestWriter;

    public ChargeRequest createChargeRequest(
            String userReferenceId,
            String senderWalletAddress,
            ChargeNetworkPolicy networkPolicy,
            String transactionHash,
            BigDecimal requestedUsdcAmount
    ) {
        String normalizedTransactionHash =
                normalizeTransactionHash(transactionHash);

        BigDecimal normalizedUsdcAmount =
                normalizeUsdcAmount(requestedUsdcAmount);

        Optional<ChargeRequest> existingRequest =
                chargeRequestReader.findByChainAndTransactionHash(
                        networkPolicy.chainId(),
                        normalizedTransactionHash
                );

        if (existingRequest.isPresent()) {
            return ensureTransactionOwnedBy(
                    existingRequest.get(),
                    userReferenceId
            );
        }

        ChargeRequest newRequest = ChargeRequest.pending(
                userReferenceId,
                senderWalletAddress,
                networkPolicy.chainId(),
                normalizedTransactionHash,
                normalizedUsdcAmount
        );

        try {
            return chargeRequestWriter.saveNewRequest(newRequest);
        } catch (CoreException exception) {
            if (exception.getErrorType() != ErrorType.CHARGE_REQUEST_ALREADY_EXISTS) {
                throw exception;
            }

            ChargeRequest savedRequest = chargeRequestReader
                    .findByChainAndTransactionHash(
                            networkPolicy.chainId(),
                            normalizedTransactionHash
                    )
                    .orElseThrow(() -> exception);

            return ensureTransactionOwnedBy(savedRequest, userReferenceId);
        }
    }

    public ChargeRequest getOwnedCharge(
            Long chargeRequestId,
            String userReferenceId
    ) {
        ChargeRequest chargeRequest =
                chargeRequestReader.read(chargeRequestId);

        chargeRequest.ensureOwnedBy(userReferenceId);

        return chargeRequest;
    }

    private ChargeRequest ensureTransactionOwnedBy(
            ChargeRequest existingRequest,
            String userReferenceId
    ) {
        if (!existingRequest.isOwnedBy(userReferenceId)) {
            throw new CoreException(
                    ErrorType.CHARGE_TRANSACTION_ALREADY_USED
            );
        }

        return existingRequest;
    }

    private String normalizeTransactionHash(
            String transactionHash
    ) {
        return transactionHash.toLowerCase(Locale.ROOT);
    }

    private BigDecimal normalizeUsdcAmount(
            BigDecimal requestedUsdcAmount
    ) {
        return requestedUsdcAmount.setScale(
                6,
                RoundingMode.UNNECESSARY
        );
    }
}
