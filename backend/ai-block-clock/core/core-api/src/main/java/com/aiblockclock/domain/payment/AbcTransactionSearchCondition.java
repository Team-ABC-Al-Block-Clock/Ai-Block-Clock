package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

// 무엇을 볼지(기간·유형)만 담는다. 어디부터 몇 개(cursor·size)는 페이지 넘김 정보라 따로 다닌다.
public record AbcTransactionSearchCondition(
        Instant startAtInclusive,
        Instant endAtExclusive,
        LedgerEventType eventType
) {
    // 장부 시각은 서버 시계(ClockConfig, UTC)로 기록되므로 날짜 경계도 같은 기준으로 잡는다.
    private static final ZoneOffset LEDGER_ZONE = ZoneOffset.UTC;

    public AbcTransactionSearchCondition {
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

    public static AbcTransactionSearchCondition allDates(LedgerEventType eventType) {
        return new AbcTransactionSearchCondition(null, null, eventType);
    }

    public static AbcTransactionSearchCondition betweenDates(
            LocalDate startDate,
            LocalDate endDate,
            LedgerEventType eventType
    ) {
        return new AbcTransactionSearchCondition(
                startDate.atStartOfDay(LEDGER_ZONE).toInstant(),
                endDate.plusDays(1).atStartOfDay(LEDGER_ZONE).toInstant(),
                eventType
        );
    }
}
