package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.payment.WithdrawalEntity;

final class WithdrawalEntityConverter {

    private WithdrawalEntityConverter() {
    }

    static Withdrawal toDomain(WithdrawalEntity entity) {
        return Withdrawal.restore(
                entity.getId(),
                WithdrawalRequestId.of(entity.getRequestId()),
                UserId.of(entity.getRequesterId()),
                AbcAmount.of(entity.getAmountAbc()),
                entity.getWalletAddress(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }

    static WithdrawalEntity toNewEntity(Withdrawal withdrawal) {
        return WithdrawalEntity.of(
                withdrawal.getRequestId().value(),
                withdrawal.getRequester().value(),
                withdrawal.getAmount().value(),
                withdrawal.getWalletAddress(),
                withdrawal.getStatus()
        );
    }
}
