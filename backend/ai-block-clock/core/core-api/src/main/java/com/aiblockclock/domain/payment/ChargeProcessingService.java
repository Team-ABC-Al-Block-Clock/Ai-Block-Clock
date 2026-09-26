package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

// 충전 요청 하나를 체인에서 확인해 확정·실패·대기 중 하나로 옮긴다.
// 체인 조회는 트랜잭션 밖에서 하고, 상태를 바꾸는 쓰기만 각자 짧은 트랜잭션으로 한다 (ARCHITECTURE 4.2).
@Service
@RequiredArgsConstructor
public class ChargeProcessingService {

    private final ChargeRequestReader chargeRequestReader;
    private final OnChainTransferReader onChainTransferReader;
    private final OnChainChargeVerifier onChainChargeVerifier;
    private final AbcConversionPolicy abcConversionPolicy;
    private final ChargeRequestWriter chargeRequestWriter;
    private final ChargeConfirmationManager chargeConfirmationManager;
    private final ChargeProperties chargeProperties;

    public List<Long> readPendingChargeIds() {
        return chargeRequestReader.readPendingIds();
    }

    public ChargeProcessResult process(Long chargeRequestId) {
        try {
            return verify(chargeRequestReader.read(chargeRequestId));
        } catch (CoreException exception) {
            if (exception.getErrorType() != ErrorType.CHARGE_REQUEST_CONCURRENTLY_MODIFIED) {
                throw exception;
            }

            // 요청 직후 확인과 주기 재확인이 같은 요청을 동시에 옮겼다. 먼저 옮긴 쪽의 결과를 돌려준다
            return ChargeProcessResult.reached(chargeRequestReader.read(chargeRequestId));
        }
    }

    private ChargeProcessResult verify(ChargeRequest chargeRequest) {
        if (!chargeRequest.isPending()) {
            return ChargeProcessResult.reached(chargeRequest);
        }

        ChargeNetworkPolicy chargeNetworkPolicy = chargeProperties.networkPolicy();

        if (!chargeNetworkPolicy.supportsChain(chargeRequest.getChainId())) {
            return ChargeProcessResult.reached(rejectCharge(chargeRequest, ChargeStatusReason.CHAIN_ID_MISMATCH));
        }

        ChargeVerificationCriteria verificationCriteria =
                chargeNetworkPolicy.createVerificationCriteria(chargeRequest);
        OnChainTokenTransfer onChainTokenTransfer = onChainTransferReader.read(verificationCriteria);
        ChargeVerificationResult verificationResult =
                onChainChargeVerifier.verify(verificationCriteria, onChainTokenTransfer);

        return switch (verificationResult.outcome()) {
            case UNREACHABLE -> ChargeProcessResult.unreached(chargeRequest);
            case WAITING -> ChargeProcessResult.reached(closeIfOverdue(chargeRequest));
            case REJECTED -> ChargeProcessResult.reached(rejectCharge(chargeRequest, verificationResult.reason()));
            case VERIFIED -> ChargeProcessResult.reached(confirmCharge(chargeRequest));
        };
    }

    // 체인이 "아직"이라고 답한 경우에만 한도를 잰다. 체인에 닿지 못한 동안은 거래가 이미 성공했을 수 있다
    private ChargeRequest closeIfOverdue(ChargeRequest chargeRequest) {
        if (!chargeRequest.isVerificationOverdue(Instant.now(), chargeProperties.verificationTimeout())) {
            return chargeRequest;
        }

        return rejectCharge(chargeRequest, ChargeStatusReason.VERIFICATION_TIMEOUT);
    }

    private ChargeRequest rejectCharge(
            ChargeRequest chargeRequest,
            ChargeStatusReason failureReason
    ) {
        chargeRequest.fail(failureReason);
        return chargeRequestWriter.updateFromPending(chargeRequest);
    }

    private ChargeRequest confirmCharge(
            ChargeRequest chargeRequest
    ) {
        AbcAmount creditedAbcAmount =
                abcConversionPolicy.convert(
                        chargeRequest.getRequestedDepositAmount()
                );

        return chargeConfirmationManager.confirm(
                chargeRequest,
                creditedAbcAmount
        );
    }
}
