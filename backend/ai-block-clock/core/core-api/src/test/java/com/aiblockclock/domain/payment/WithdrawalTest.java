package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.WithdrawalStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalTest {
    private static final UserId OWNER = UserId.of(7L);

    @Test
    void 새_신청은_심사_대기다() {
        Withdrawal withdrawal = Withdrawal.requested(WithdrawalRequestId.of(UUID.randomUUID()), OWNER,
                AbcAmount.of(BigDecimal.TEN), "0x1111111111111111111111111111111111111111");

        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.REQUESTED);
        assertThat(withdrawal.isRequestedBy(OWNER)).isTrue();
        assertThat(withdrawal.isRequestedBy(UserId.of(8L))).isFalse();
    }

    @Test
    void 심사_대기인_신청을_취소하면_취소_상태가_된다() {
        Withdrawal withdrawal = saved(WithdrawalStatus.REQUESTED);

        withdrawal.cancel();

        assertThat(withdrawal.isCanceled()).isTrue();
    }

    @Test
    void 심사_대기가_아닌_신청은_취소할_수_없다() {
        Withdrawal withdrawal = saved(WithdrawalStatus.CANCELED);

        assertThatThrownBy(withdrawal::cancel)
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_INVALID_STATE));
    }

    private Withdrawal saved(WithdrawalStatus status) {
        return Withdrawal.restore(31L, WithdrawalRequestId.of(UUID.randomUUID()), OWNER, AbcAmount.of(BigDecimal.TEN),
                "0x1111111111111111111111111111111111111111", status, Instant.parse("2026-09-26T09:00:00Z"));
    }
}
