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

    // 현재 입금 토큰인 USDC는 소수점 이하 6자리까지 지원한다.
    private static final int DEPOSIT_TOKEN_DECIMAL_PLACES = 6;

    private final ChargeRequestReader chargeRequestReader;
    private final ChargeRequestWriter chargeRequestWriter;

    // 중복 저장 실패 후 재조회가 이전 트랜잭션에 합류하지 않도록 외부 트랜잭션 없이 호출한다.
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

            // 저장 롤백이 끝난 뒤 Reader가 새 조회 트랜잭션을 시작한다.
            // MySQL에서 이전 조회의 스냅샷을 재사용하지 않고 다른 요청이 저장한 거래를 확인하기 위함이다.
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
        if (requestedUsdcAmount == null) {
            throw new CoreException(
                    ErrorType.CHARGE_INVALID_AMOUNT
            );
        }

        try {
            return requestedUsdcAmount.setScale(
                    DEPOSIT_TOKEN_DECIMAL_PLACES,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new CoreException(
                    ErrorType.CHARGE_INVALID_AMOUNT
            );
        }
    }
}
