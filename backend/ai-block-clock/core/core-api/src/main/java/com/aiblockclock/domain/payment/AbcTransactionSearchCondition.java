package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

public record AbcTransactionSearchCondition(
        int page,
        Instant startAtInclusive,
        Instant endAtExclusive,
        LedgerEventType eventType
) {
    // 장부 시각은 서버 시계(ClockConfig, UTC)로 기록되므로 날짜 경계도 같은 기준으로 잡는다.
    private static final ZoneOffset LEDGER_ZONE = ZoneOffset.UTC;

    public AbcTransactionSearchCondition {
        if (page < 1) {
            throw new CoreException(ErrorType.INVALID_REQUEST, Map.of("page", page));
        }

        boolean hasStart = startAtInclusive != null;
        boolean hasEnd = endAtExclusive != null;

        if (hasStart != hasEnd) {
            throw new CoreException(ErrorType.INVALID_REQUEST,
                    Map.of("hasStart", hasStart, "hasEnd", hasEnd));
        }

        if (hasStart && !startAtInclusive.isBefore(endAtExclusive)) {
            throw new CoreException(ErrorType.INVALID_REQUEST,
                    Map.of("startAt", startAtInclusive, "endAt", endAtExclusive));
        }
    }

    public static AbcTransactionSearchCondition allDates(
            int page,
            LedgerEventType eventType
    ) {
        return new AbcTransactionSearchCondition(
                page,
                null,
                null,
                eventType
        );
    }

    public static AbcTransactionSearchCondition betweenDates(
            int page,
            LocalDate startDate,
            LocalDate endDate,
            LedgerEventType eventType
    ) {
        return new AbcTransactionSearchCondition(
                page,
                startDate.atStartOfDay(LEDGER_ZONE).toInstant(),
                endDate.plusDays(1).atStartOfDay(LEDGER_ZONE).toInstant(),
                eventType
        );
    }
}
