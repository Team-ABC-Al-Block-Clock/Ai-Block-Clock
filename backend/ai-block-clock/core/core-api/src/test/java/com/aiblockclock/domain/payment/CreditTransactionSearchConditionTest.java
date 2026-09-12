package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerEventType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditTransactionSearchConditionTest {

    @Test
    void 페이지는_1부터_시작한다() {
        assertThatThrownBy(
                () -> CreditTransactionSearchCondition
                        .allDates(0, null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "페이지 번호는 1 이상이어야 합니다."
                );
    }

    @Test
    void 종료일은_다음_날_0시_미만으로_변환한다() {
        CreditTransactionSearchCondition condition =
                CreditTransactionSearchCondition.betweenDates(
                        1,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 12),
                        LedgerEventType.RESERVE
                );

        assertThat(condition.startAtInclusive())
                .isEqualTo(
                        LocalDateTime.of(
                                2026, 9, 1,
                                0, 0
                        )
                );

        assertThat(condition.endAtExclusive())
                .isEqualTo(
                        LocalDateTime.of(
                                2026, 9, 13,
                                0, 0
                        )
                );

        assertThat(condition.eventType())
                .isEqualTo(LedgerEventType.RESERVE);
    }

    @Test
    void 시작일이_종료일보다_늦으면_거절한다() {
        assertThatThrownBy(
                () -> CreditTransactionSearchCondition
                        .betweenDates(
                                1,
                                LocalDate.of(2026, 9, 12),
                                LocalDate.of(2026, 9, 1),
                                null
                        )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "조회 시작일은 종료일보다 늦을 수 없습니다."
                );
    }

    @Test
    void 기간을_생략하면_시작과_종료_시각이_없다() {
        CreditTransactionSearchCondition condition =
                CreditTransactionSearchCondition
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
