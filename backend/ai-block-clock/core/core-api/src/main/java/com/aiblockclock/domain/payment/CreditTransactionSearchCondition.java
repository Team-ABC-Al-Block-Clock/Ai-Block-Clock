package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerEventType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public record CreditTransactionSearchCondition(
        int page,
        LocalDateTime startAtInclusive,
        LocalDateTime endAtExclusive,
        LedgerEventType eventType
) {

    public CreditTransactionSearchCondition {
        if (page < 1) {
            throw new IllegalArgumentException(
                    "페이지 번호는 1 이상이어야 합니다."
            );
        }

        boolean hasStart = startAtInclusive != null;
        boolean hasEnd = endAtExclusive != null;

        if (hasStart != hasEnd) {
            throw new IllegalArgumentException(
                    "조회 시작일과 종료일은 함께 필요합니다."
            );
        }

        if (hasStart
                && !startAtInclusive.isBefore(endAtExclusive)) {
            throw new IllegalArgumentException(
                    "조회 시작일은 종료일보다 늦을 수 없습니다."
            );
        }
    }

    public static CreditTransactionSearchCondition allDates(
            int page,
            LedgerEventType eventType
    ) {
        return new CreditTransactionSearchCondition(
                page,
                null,
                null,
                eventType
        );
    }

    public static CreditTransactionSearchCondition betweenDates(
            int page,
            LocalDate startDate,
            LocalDate endDate,
            LedgerEventType eventType
    ) {
        Objects.requireNonNull(startDate);
        Objects.requireNonNull(endDate);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "조회 시작일은 종료일보다 늦을 수 없습니다."
            );
        }

        return new CreditTransactionSearchCondition(
                page,
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay(),
                eventType
        );
    }
}
