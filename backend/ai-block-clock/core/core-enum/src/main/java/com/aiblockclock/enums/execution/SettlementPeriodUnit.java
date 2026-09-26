package com.aiblockclock.enums.execution;

import java.time.LocalDate;

// 수익 통계를 묶는 단위다. 날짜가 속한 묶음의 첫날로 같은 묶음을 가린다.
public enum SettlementPeriodUnit {
    DAY {
        @Override
        public LocalDate startOf(LocalDate date) {
            return date;
        }
    },
    MONTH {
        @Override
        public LocalDate startOf(LocalDate date) {
            return date.withDayOfMonth(1);
        }
    };

    public abstract LocalDate startOf(LocalDate date);
}
