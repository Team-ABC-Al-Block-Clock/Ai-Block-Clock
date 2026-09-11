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
        validateUserReferenceId(userReferenceId);
        validateWalletAddress(senderWalletAddress);
        validateNetworkPolicy(networkPolicy);

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
        validateUserReferenceId(userReferenceId);

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

    private void validateUserReferenceId(
            String userReferenceId
    ) {
        if (userReferenceId == null
                || userReferenceId.isBlank()) {
            throw new IllegalArgumentException(
                    "사용자 식별값은 필수입니다."
            );
        }
    }

    private void validateWalletAddress(
            String senderWalletAddress
    ) {
        if (senderWalletAddress == null
                || !senderWalletAddress.matches(
                "^0x[0-9a-fA-F]{40}$"
        )) {
            throw new IllegalArgumentException(
                    "올바른 지갑 주소가 필요합니다."
            );
        }
    }

    private void validateNetworkPolicy(
            ChargeNetworkPolicy networkPolicy
    ) {
        if (networkPolicy == null) {
            throw new IllegalArgumentException(
                    "충전 네트워크 설정은 필수입니다."
            );
        }
    }

    private String normalizeTransactionHash(
            String transactionHash
    ) {
        if (transactionHash == null
                || !transactionHash.matches(
                "^0x[0-9a-fA-F]{64}$"
        )) {
            throw new IllegalArgumentException(
                    "올바른 트랜잭션 해시가 필요합니다."
            );
        }

        return transactionHash.toLowerCase(Locale.ROOT);
    }

    private BigDecimal normalizeUsdcAmount(
            BigDecimal requestedUsdcAmount
    ) {
        if (requestedUsdcAmount == null
                || requestedUsdcAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "요청 USDC 수량은 0보다 커야 합니다."
            );
        }

        try {
            return requestedUsdcAmount.setScale(
                    6,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "USDC 수량은 소수점 최대 6자리까지 허용합니다."
            );
        }
    }
}
