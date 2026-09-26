package com.aiblockclock.domain;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatePeriodTest {

    @Test
    void 종료일은_그날을_포함하도록_다음_날_0시_미만으로_잡는다() {
        DatePeriod period = DatePeriod.of(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 25));

        assertThat(period.startAtInclusive()).isEqualTo(Instant.parse("2026-09-25T00:00:00Z"));
        assertThat(period.endAtExclusive()).isEqualTo(Instant.parse("2026-09-26T00:00:00Z"));
    }

    @Test
    void 두_날짜를_모두_생략하면_기간을_가리지_않는다() {
        DatePeriod period = DatePeriod.of(null, null);

        assertThat(period.startAtInclusive()).isNull();
        assertThat(period.endAtExclusive()).isNull();
    }

    @Test
    void 한쪽_날짜만_있으면_거절한다() {
        assertThatThrownBy(() -> DatePeriod.of(LocalDate.of(2026, 9, 25), null))
                .isInstanceOfSatisfying(CoreException.class,
                        exception -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST));
    }

    @Test
    void 시작일이_종료일보다_늦으면_거절한다() {
        assertThatThrownBy(() -> DatePeriod.of(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 25)))
                .isInstanceOfSatisfying(CoreException.class,
                        exception -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST));
    }
}
