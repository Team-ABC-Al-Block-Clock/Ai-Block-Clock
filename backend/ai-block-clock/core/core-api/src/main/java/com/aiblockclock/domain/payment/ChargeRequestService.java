package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChargeRequestService {

    private final ChargeRequestReader chargeRequestReader;
    private final ChargeRequestWriter chargeRequestWriter;
    private final UserReader userReader;
    private final ChargeProperties chargeProperties;

    // 중복 저장 실패 후 재조회가 이전 트랜잭션에 합류하지 않도록 외부 트랜잭션 없이 호출한다.
    // 보낸 지갑은 요청 본문이 아니라 로그인한 지갑이다. 남의 입금 해시를 들고 와도 보낸 사람이 달라 거절된다.
    public ChargeRequest createChargeRequest(
            UserId requester,
            String transactionHash,
            BigDecimal requestedDepositAmountValue
    ) {
        ChargeNetworkPolicy networkPolicy = chargeProperties.networkPolicy();
        String senderWalletAddress = userReader.read(requester).walletAddress();

        if (networkPolicy.isCustody(senderWalletAddress)) {
            throw new CoreException(ErrorType.CHARGE_SENDER_IS_CUSTODY);
        }

        String userReferenceId = LedgerTransaction.referenceIdOf(requester);
        String normalizedTransactionHash =
                normalizeTransactionHash(transactionHash);

        DepositAmount requestedDepositAmount =
                DepositAmount.of(requestedDepositAmountValue);

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
                requestedDepositAmount
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
            UserId requester
    ) {
        ChargeRequest chargeRequest =
                chargeRequestReader.read(chargeRequestId);

        chargeRequest.ensureOwnedBy(LedgerTransaction.referenceIdOf(requester));

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

}
