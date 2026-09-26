package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalPolicyTest {
    private static final WithdrawalPolicy POLICY = new WithdrawalPolicy(
            AbcAmount.of(BigDecimal.ONE), AbcAmount.of(new BigDecimal("1000")), Duration.ofHours(24));
    private static final Instant NOW = Instant.parse("2026-09-26T09:00:00Z");
    private static final RecentWithdrawals NONE = new RecentWithdrawals(false, null);
    private static final BigDecimal PAYOUT = new BigDecimal("1500");

    @ParameterizedTest
    @ValueSource(strings = {"1", "10.5", "1000"})
    void 범위_안의_금액은_정산금이_넉넉하면_신청할_수_있다(String amount) {
        assertThatCode(() -> POLICY.ensureAllowed(amountOf(amount), PAYOUT, NONE, NOW))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.999999", "1000.000001"})
    void 범위를_벗어난_금액은_최소와_최대를_알려_주며_막는다(String amount) {
        assertThatThrownBy(() -> POLICY.ensureAllowed(amountOf(amount), PAYOUT, NONE, NOW))
                .isInstanceOfSatisfying(CoreException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_AMOUNT_OUT_OF_RANGE);
                    assertThat(exception.getData()).isEqualTo(Map.of(
                            "minimumAmount", "1.000000", "maximumAmount", "1000.000000"));
                });
    }

    @Test
    void 처리_중인_출금이_있으면_막는다() {
        assertThatThrownBy(() -> POLICY.ensureAllowed(
                amountOf("10"), PAYOUT, new RecentWithdrawals(true, NOW.minus(Duration.ofDays(3))), NOW))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_ALREADY_IN_PROGRESS));
    }

    @Test
    void 마지막_신청에서_주기가_지나지_않았으면_다시_신청할_수_있는_시각을_알려_주며_막는다() {
        Instant last = NOW.minus(Duration.ofHours(23));

        assertThatThrownBy(() -> POLICY.ensureAllowed(amountOf("10"), PAYOUT, new RecentWithdrawals(false, last), NOW))
                .isInstanceOfSatisfying(CoreException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_TOO_FREQUENT);
                    assertThat(exception.getData()).isEqualTo(Map.of("availableAt", last.plus(Duration.ofHours(24))));
                });
    }

    @Test
    void 주기가_딱_지난_시각부터_다시_신청할_수_있다() {
        RecentWithdrawals recent = new RecentWithdrawals(false, NOW.minus(Duration.ofHours(24)));

        assertThatCode(() -> POLICY.ensureAllowed(amountOf("10"), PAYOUT, recent, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void 주기가_0이면_처리_중인_출금만_없으면_곧바로_다시_신청할_수_있다() {
        WithdrawalPolicy noInterval = new WithdrawalPolicy(
                AbcAmount.of(BigDecimal.ONE), AbcAmount.of(new BigDecimal("1000")), Duration.ZERO);

        assertThatCode(() -> noInterval.ensureAllowed(amountOf("10"), PAYOUT, new RecentWithdrawals(false, NOW), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void 정산금보다_많이_신청하면_지금_정산금을_알려_주며_막는다() {
        assertThatThrownBy(() -> POLICY.ensureAllowed(amountOf("10"), new BigDecimal("9.999999"), NONE, NOW))
                .isInstanceOfSatisfying(CoreException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_PAYOUT_INSUFFICIENT);
                    assertThat(exception.getData()).isEqualTo(Map.of("payoutAmount", "9.999999"));
                });
    }

    @Test
    void 정산금을_모두_출금할_수_있다() {
        assertThatCode(() -> POLICY.ensureAllowed(amountOf("10"), new BigDecimal("10.000000"), NONE, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void 여러_규칙에_걸리면_금액_범위부터_알려_준다() {
        assertThatThrownBy(() -> POLICY.ensureAllowed(
                amountOf("0.5"), BigDecimal.ZERO, new RecentWithdrawals(true, NOW), NOW))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.WITHDRAWAL_AMOUNT_OUT_OF_RANGE));
    }

    private AbcAmount amountOf(String value) {
        return AbcAmount.of(new BigDecimal(value));
    }
}
