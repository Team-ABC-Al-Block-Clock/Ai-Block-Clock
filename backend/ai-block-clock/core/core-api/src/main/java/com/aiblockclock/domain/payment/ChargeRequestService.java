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

    public ChargeRequest createPending(
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

        Optional<ChargeRequest> existingChargeRequest =
                chargeRequestReader.findByChainAndTransactionHash(
                        networkPolicy.chainId(),
                        normalizedTransactionHash
                );

        if (existingChargeRequest.isPresent()) {
            return ensureReusableByUser(
                    existingChargeRequest.get(),
                    userReferenceId
            );
        }

        ChargeRequest newChargeRequest = ChargeRequest.pending(
                userReferenceId,
                senderWalletAddress,
                networkPolicy.chainId(),
                normalizedTransactionHash,
                normalizedUsdcAmount
        );

        return chargeRequestWriter.append(newChargeRequest);
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

    private ChargeRequest ensureReusableByUser(
            ChargeRequest existingChargeRequest,
            String userReferenceId
    ) {
        if (!existingChargeRequest.isOwnedBy(userReferenceId)) {
            throw new CoreException(
                    ErrorType.CHARGE_TRANSACTION_ALREADY_USED
            );
        }

        return existingChargeRequest;
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
