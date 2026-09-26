package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargeRequestTest {

    private static final Instant CONFIRMED_AT =
            Instant.parse("2026-09-14T06:00:00Z");

    @Test
    void 새로운_충전_요청은_PENDING_상태이다() {
        ChargeRequest chargeRequest = createPendingChargeRequest();

        assertThat(chargeRequest.getStatus())
                .isEqualTo(ChargeStatus.PENDING);
        assertThat(chargeRequest.getCreditedAbcAmount())
                .isNull();
    }

    @Test
    void 대기_중인_충전_요청을_확정한다() {
        ChargeRequest chargeRequest = createPendingChargeRequest();

        chargeRequest.confirm(
                AbcAmount.of(new BigDecimal("10.000")),
                CONFIRMED_AT
        );

        assertThat(chargeRequest.getStatus())
                .isEqualTo(ChargeStatus.CONFIRMED);
        assertThat(chargeRequest.getCreditedAbcAmount().value())
                .isEqualByComparingTo("10.000");
        assertThat(chargeRequest.getConfirmedAt())
                .isEqualTo(CONFIRMED_AT);
    }

    @Test
    void 지급할_ABC가_0보다_크지_않으면_확정할_수_없다() {
        ChargeRequest chargeRequest = createPendingChargeRequest();

        assertThatThrownBy(
                () -> chargeRequest.confirm(
                        null,
                        CONFIRMED_AT
                )
        ).isInstanceOfSatisfying(
                CoreException.class,
                exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.CHARGE_INVALID_AMOUNT)
        );
    }

    @Test
    void 대기_중인_충전_요청을_실패_처리한다() {
        ChargeRequest chargeRequest = createPendingChargeRequest();

        chargeRequest.fail(
                ChargeStatusReason.AMOUNT_MISMATCH
        );

        assertThat(chargeRequest.getStatus())
                .isEqualTo(ChargeStatus.FAILED);
        assertThat(chargeRequest.getStatusReason())
                .isEqualTo(ChargeStatusReason.AMOUNT_MISMATCH);
    }

    @Test
    void 충전_요청_취소는_여러_번_호출해도_같은_결과를_유지한다() {
        ChargeRequest chargeRequest = createPendingChargeRequest();

        chargeRequest.cancel();

        assertThatCode(chargeRequest::cancel)
                .doesNotThrowAnyException();
        assertThat(chargeRequest.getStatus())
                .isEqualTo(ChargeStatus.CANCELED);
        assertThat(chargeRequest.getStatusReason())
                .isEqualTo(ChargeStatusReason.USER_CANCELED);
    }

    @Test
    void 대기_상태가_아닌_충전_요청은_다른_상태로_변경할_수_없다() {
        ChargeRequest chargeRequest = createPendingChargeRequest();
        chargeRequest.cancel();

        assertThatThrownBy(
                () -> chargeRequest.confirm(
                        AbcAmount.of(new BigDecimal("10.000")),
                        CONFIRMED_AT
                )
        ).isInstanceOfSatisfying(
                CoreException.class,
                exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.CHARGE_INVALID_STATE)
        );
    }

    @Test
    void 다른_사용자는_충전_요청을_소유하지_않는다() {
        ChargeRequest chargeRequest = createPendingChargeRequest();

        assertThatThrownBy(
                () -> chargeRequest.ensureOwnedBy("other-user")
        ).isInstanceOfSatisfying(
                CoreException.class,
                exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.CHARGE_REQUEST_FORBIDDEN)
        );
    }

    private ChargeRequest createPendingChargeRequest() {
        return ChargeRequest.pending(
                "user-request-100",
                "0x1111111111111111111111111111111111111111",
                12345L,
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                DepositAmount.of(
                        new BigDecimal("10.000000")
                )
        );
    }

    @Test
    void 요청_시각부터_대기_한도가_지나면_확인_기한을_넘긴_것이다() {
        Instant requestedAt = Instant.parse("2026-09-25T00:00:00Z");
        ChargeRequest chargeRequest = savedPendingAt(requestedAt);

        assertThat(chargeRequest.isVerificationOverdue(requestedAt.plus(Duration.ofMinutes(29)), Duration.ofMinutes(30)))
                .isFalse();
        assertThat(chargeRequest.isVerificationOverdue(requestedAt.plus(Duration.ofMinutes(30)), Duration.ofMinutes(30)))
                .isTrue();
    }

    @Test
    void 저장되기_전_요청은_기한을_재지_않는다() {
        ChargeRequest chargeRequest = ChargeRequest.pending("user", "0x1111111111111111111111111111111111111111",
                12345L, "0xhash", DepositAmount.of(BigDecimal.ONE));

        assertThat(chargeRequest.isVerificationOverdue(Instant.MAX, Duration.ZERO)).isFalse();
    }

    @Test
    void 보낸_사람_금액_대기_한도로_닫힌_요청은_다시_열_수_있다() {
        for (ChargeStatusReason reason : new ChargeStatusReason[]{
                ChargeStatusReason.SENDER_WALLET_MISMATCH,
                ChargeStatusReason.AMOUNT_MISMATCH,
                ChargeStatusReason.VERIFICATION_TIMEOUT}) {
            ChargeRequest chargeRequest = savedPendingAt(Instant.EPOCH);
            chargeRequest.fail(reason);

            assertThat(chargeRequest.isReopenable()).as(reason.name()).isTrue();
        }
    }

    @Test
    void 거래_자체가_어긋나_닫힌_요청은_다시_열지_않는다() {
        ChargeRequest chargeRequest = savedPendingAt(Instant.EPOCH);
        chargeRequest.fail(ChargeStatusReason.ONCHAIN_TRANSACTION_FAILED);

        assertThat(chargeRequest.isReopenable()).isFalse();
    }

    @Test
    void 확인_중이거나_적립된_요청은_다시_열_대상이_아니다() {
        ChargeRequest pending = savedPendingAt(Instant.EPOCH);
        ChargeRequest confirmed = savedPendingAt(Instant.EPOCH);
        confirmed.confirm(AbcAmount.of(BigDecimal.ONE), Instant.EPOCH);

        assertThat(pending.isReopenable()).isFalse();
        assertThat(confirmed.isReopenable()).isFalse();
    }

    private ChargeRequest savedPendingAt(Instant requestedAt) {
        return ChargeRequest.restore(1L, "user", "0x1111111111111111111111111111111111111111", 12345L, "0xhash",
                DepositAmount.of(BigDecimal.ONE), requestedAt, null, ChargeStatus.PENDING, null, null, 0L);
    }
}
