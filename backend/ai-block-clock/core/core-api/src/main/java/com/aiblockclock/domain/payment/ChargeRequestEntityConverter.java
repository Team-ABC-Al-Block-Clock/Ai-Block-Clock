package com.aiblockclock.domain.payment;

import com.aiblockclock.storage.payment.ChargeRequestEntity;

final class ChargeRequestEntityConverter {

    private ChargeRequestEntityConverter() {
    }

    static ChargeRequest toDomain(
            ChargeRequestEntity entity
    ) {
        return ChargeRequest.restore(
                entity.getId(),
                entity.getUserReferenceId(),
                entity.getSenderWalletAddress(),
                entity.getChainId(),
                entity.getTransactionHash(),
                entity.getRequestedUsdcAmount(),
                entity.getCreditedAbcAmount(),
                entity.getStatus(),
                entity.getStatusReason(),
                entity.getConfirmedAt(),
                entity.getVersion()
        );
    }

    static ChargeRequestEntity toNewEntity(
            ChargeRequest chargeRequest
    ) {
        return ChargeRequestEntity.pending(
                chargeRequest.getUserReferenceId(),
                chargeRequest.getSenderWalletAddress(),
                chargeRequest.getChainId(),
                chargeRequest.getTransactionHash(),
                chargeRequest.getRequestedUsdcAmount()
        );
    }

    static void applyToEntity(
            ChargeRequest chargeRequest,
            ChargeRequestEntity entity
    ) {
        entity.applyState(
                chargeRequest.getCreditedAbcAmount(),
                chargeRequest.getStatus(),
                chargeRequest.getStatusReason(),
                chargeRequest.getConfirmedAt()
        );
    }
}
