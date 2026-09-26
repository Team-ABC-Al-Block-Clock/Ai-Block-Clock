package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.WithdrawalStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalTest {
    private static final UserId OWNER = UserId.of(7L);
    private static final UserId ADMIN = UserId.of(1L);
    private static final PayoutTransaction TRANSACTION = new PayoutTransaction("0x" + "a".repeat(64), "0xf86c");

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

    @Test
    void 승인하면_지급_대기가_되고_승인한_관리자를_적는다() {
        Withdrawal withdrawal = saved(WithdrawalStatus.REQUESTED);

        withdrawal.approve(ADMIN);

        assertThat(withdrawal.isApproved()).isTrue();
        assertThat(withdrawal.getReviewer()).isEqualTo(ADMIN);
        assertThat(withdrawal.getRejectReason()).isNull();
    }

    @Test
    void 반려하면_반려한_관리자와_사유를_적는다() {
        Withdrawal withdrawal = saved(WithdrawalStatus.REQUESTED);

        withdrawal.reject(ADMIN, "정산 내역 확인이 필요합니다");

        assertThat(withdrawal.isRejected()).isTrue();
        assertThat(withdrawal.getReviewer()).isEqualTo(ADMIN);
        assertThat(withdrawal.getRejectReason()).isEqualTo("정산 내역 확인이 필요합니다");
    }

    @ParameterizedTest
    @EnumSource(value = WithdrawalStatus.class, names = "REQUESTED", mode = EnumSource.Mode.EXCLUDE)
    void 심사_대기가_아닌_신청은_승인도_반려도_할_수_없다(WithdrawalStatus status) {
        Withdrawal withdrawal = saved(status);

        assertThatThrownBy(() -> withdrawal.approve(ADMIN))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_INVALID_STATE));
        assertThatThrownBy(() -> withdrawal.reject(ADMIN, "사유"))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_INVALID_STATE));
    }

    @Test
    void 승인된_신청은_공급자가_취소할_수_없다() {
        Withdrawal withdrawal = saved(WithdrawalStatus.APPROVED);

        assertThatThrownBy(withdrawal::cancel)
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_INVALID_STATE));
    }

    @Test
    void 승인된_신청은_서명한_거래와_함께_지급_중이_된다() {
        Withdrawal withdrawal = saved(WithdrawalStatus.APPROVED);

        withdrawal.startPayout(TRANSACTION);

        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.PAYING);
        assertThat(withdrawal.getPayoutTransaction()).isEqualTo(TRANSACTION);
    }

    @ParameterizedTest
    @EnumSource(value = WithdrawalStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void 승인되지_않은_신청은_지급을_시작할_수_없다(WithdrawalStatus status) {
        Withdrawal withdrawal = saved(status);

        assertThatThrownBy(() -> withdrawal.startPayout(TRANSACTION))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_INVALID_STATE));
    }

    @Test
    void 지급_중인_신청을_끝내면_지급_완료가_된다() {
        Withdrawal withdrawal = saved(WithdrawalStatus.PAYING);

        withdrawal.completePayout();

        assertThat(withdrawal.isPaid()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = WithdrawalStatus.class, names = "PAYING", mode = EnumSource.Mode.EXCLUDE)
    void 지급_중이_아닌_신청은_지급을_끝낼_수_없다(WithdrawalStatus status) {
        Withdrawal withdrawal = saved(status);

        assertThatThrownBy(withdrawal::completePayout)
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_INVALID_STATE));
    }

    @Test
    void 지급_중인_신청은_공급자가_취소할_수_없다() {
        Withdrawal withdrawal = saved(WithdrawalStatus.PAYING);

        assertThatThrownBy(withdrawal::cancel)
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_INVALID_STATE));
    }

    private Withdrawal saved(WithdrawalStatus status) {
        return Withdrawal.restore(31L, WithdrawalRequestId.of(UUID.randomUUID()), OWNER, AbcAmount.of(BigDecimal.TEN),
                "0x1111111111111111111111111111111111111111", status, Instant.parse("2026-09-26T09:00:00Z"),
                null, null, null, null, null, null);
    }
}
