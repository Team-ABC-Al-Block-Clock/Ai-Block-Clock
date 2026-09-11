package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.payment.ChargeNetworkPolicy;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChargeRequestService {

    private final ChargeRequestRepository chargeRequestRepository;

    @Transactional
    public ChargeRequestEntity createPending(
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

        return chargeRequestRepository
                .findByChainIdAndTransactionHash(
                        networkPolicy.chainId(),
                        normalizedTransactionHash
                )
                .map(existing -> {
                    if (!existing.getUserReferenceId()
                            .equals(userReferenceId)) {
                        throw new CoreException(
                                ErrorType.CHARGE_TRANSACTION_ALREADY_USED
                        );
                    }

                    return existing;
                })
                .orElseGet(
                        () -> chargeRequestRepository.save(
                                ChargeRequestEntity.pending(
                                        userReferenceId,
                                        senderWalletAddress,
                                        networkPolicy.chainId(),
                                        normalizedTransactionHash,
                                        normalizedUsdcAmount
                                )
                        )
                );
    }

    public ChargeRequestEntity getOwnedCharge(
            Long chargeRequestId,
            String userReferenceId
    ) {
        ChargeRequestEntity chargeRequest =
                chargeRequestRepository.findById(chargeRequestId)
                        .orElseThrow(
                                () -> new CoreException(
                                        ErrorType.CHARGE_REQUEST_NOT_FOUND
                                )
                        );

        if (!chargeRequest.getUserReferenceId()
                .equals(userReferenceId)) {
            throw new CoreException(
                    ErrorType.CHARGE_REQUEST_FORBIDDEN
            );
        }

        return chargeRequest;
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
