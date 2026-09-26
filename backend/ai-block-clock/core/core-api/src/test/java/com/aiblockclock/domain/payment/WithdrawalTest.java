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
import java.time.Duration;
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

    @ParameterizedTest
    @EnumSource(value = WithdrawalStatus.class, names = {"PAYING", "ON_HOLD"})
    void 체인에_보낸_신청은_성공하면_지급_완료가_된다(WithdrawalStatus status) {
        Withdrawal withdrawal = saved(status);

        withdrawal.completePayout();

        assertThat(withdrawal.isPaid()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = WithdrawalStatus.class, names = {"PAYING", "ON_HOLD"}, mode = EnumSource.Mode.EXCLUDE)
    void 체인에_보내지_않은_신청은_지급을_끝낼_수_없다(WithdrawalStatus status) {
        Withdrawal withdrawal = saved(status);

        assertThatThrownBy(withdrawal::completePayout)
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_INVALID_STATE));
    }

    @ParameterizedTest
    @EnumSource(value = WithdrawalStatus.class, names = {"PAYING", "ON_HOLD"})
    void 체인에_보낸_신청은_되돌려지면_실패로_닫힌다(WithdrawalStatus status) {
        Withdrawal withdrawal = saved(status);

        withdrawal.failPayout();

        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.FAILED);
    }

    @ParameterizedTest
    @EnumSource(value = WithdrawalStatus.class, names = {"PAYING", "ON_HOLD"}, mode = EnumSource.Mode.EXCLUDE)
    void 체인에_보내지_않은_신청은_실패로_닫을_수_없다(WithdrawalStatus status) {
        Withdrawal withdrawal = saved(status);

        assertThatThrownBy(withdrawal::failPayout)
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_INVALID_STATE));
    }

    @Test
    void 지급_중인_신청은_보류로_넘길_수_있다() {
        Withdrawal withdrawal = saved(WithdrawalStatus.PAYING);

        withdrawal.holdPayout();

        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.ON_HOLD);
    }

    @ParameterizedTest
    @EnumSource(value = WithdrawalStatus.class, names = "PAYING", mode = EnumSource.Mode.EXCLUDE)
    void 지급_중이_아닌_신청은_보류로_넘길_수_없다(WithdrawalStatus status) {
        Withdrawal withdrawal = saved(status);

        assertThatThrownBy(withdrawal::holdPayout)
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_INVALID_STATE));
    }

    @Test
    void 보낸_뒤_보류_기준만큼_지나야_보류로_넘길_때다() {
        Instant submittedAt = Instant.parse("2026-09-26T10:00:00Z");
        Withdrawal withdrawal = sent(WithdrawalStatus.PAYING, submittedAt);
        Duration holdAfter = Duration.ofMinutes(10);

        assertThat(withdrawal.isPayoutOverdue(holdAfter, submittedAt.plus(Duration.ofMinutes(9)))).isFalse();
        assertThat(withdrawal.isPayoutOverdue(holdAfter, submittedAt.plus(holdAfter))).isTrue();
    }

    @Test
    void 이미_보류인_신청은_다시_보류로_넘길_때가_아니다() {
        Instant submittedAt = Instant.parse("2026-09-26T10:00:00Z");
        Withdrawal withdrawal = sent(WithdrawalStatus.ON_HOLD, submittedAt);

        assertThat(withdrawal.isPayoutOverdue(Duration.ofMinutes(10), submittedAt.plus(Duration.ofHours(1)))).isFalse();
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

    private Withdrawal sent(WithdrawalStatus status, Instant submittedAt) {
        return Withdrawal.restore(31L, WithdrawalRequestId.of(UUID.randomUUID()), OWNER, AbcAmount.of(BigDecimal.TEN),
                "0x1111111111111111111111111111111111111111", status, Instant.parse("2026-09-26T09:00:00Z"),
                ADMIN, Instant.parse("2026-09-26T09:30:00Z"), null, TRANSACTION, submittedAt, null);
    }
}
