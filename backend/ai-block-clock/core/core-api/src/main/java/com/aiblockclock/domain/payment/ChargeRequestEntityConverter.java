package com.aiblockclock.domain.payment;

import com.aiblockclock.storage.payment.ChargeRequestEntity;

import java.math.BigDecimal;

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
                DepositAmount.of(entity.getRequestedUsdcAmount()),
                toAbcAmount(entity.getCreditedAbcAmount()),
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
                chargeRequest.getRequestedDepositAmount().value()
        );
    }

    static BigDecimal toStorageValue(AbcAmount abcAmount) {
        return abcAmount == null ? null : abcAmount.value();
    }

    private static AbcAmount toAbcAmount(BigDecimal value) {
        return value == null ? null : AbcAmount.of(value);
    }
}
