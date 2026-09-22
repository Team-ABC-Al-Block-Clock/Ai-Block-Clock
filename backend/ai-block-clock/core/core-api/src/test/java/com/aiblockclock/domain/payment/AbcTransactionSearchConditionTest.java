package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbcTransactionSearchConditionTest {

    @Test
    void 페이지는_1부터_시작한다() {
        assertThatThrownBy(
                () -> AbcTransactionSearchCondition
                        .allDates(0, null)
        ).isInstanceOfSatisfying(CoreException.class,
                exception -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST));
    }

    @Test
    void 종료일은_다음_날_0시_미만으로_변환한다() {
        AbcTransactionSearchCondition condition =
                AbcTransactionSearchCondition.betweenDates(
                        1,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 12),
                        LedgerEventType.RESERVE
                );

        assertThat(condition.startAtInclusive())
                .isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));

        assertThat(condition.endAtExclusive())
                .isEqualTo(Instant.parse("2026-09-13T00:00:00Z"));

        assertThat(condition.eventType())
                .isEqualTo(LedgerEventType.RESERVE);
    }

    @Test
    void 시작일이_종료일보다_늦으면_거절한다() {
        assertThatThrownBy(
                () -> AbcTransactionSearchCondition
                        .betweenDates(
                                1,
                                LocalDate.of(2026, 9, 12),
                                LocalDate.of(2026, 9, 1),
                                null
                        )
        ).isInstanceOfSatisfying(CoreException.class,
                exception -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST));
    }

    @Test
    void 기간을_생략하면_시작과_종료_시각이_없다() {
        AbcTransactionSearchCondition condition =
                AbcTransactionSearchCondition
                        .allDates(
                                1,
                                LedgerEventType.CHARGE
                        );

        assertThat(condition.startAtInclusive()).isNull();
        assertThat(condition.endAtExclusive()).isNull();
        assertThat(condition.eventType())
                .isEqualTo(LedgerEventType.CHARGE);
    }
}
