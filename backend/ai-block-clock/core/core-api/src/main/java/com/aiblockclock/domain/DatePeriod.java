package com.aiblockclock.domain;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

// 날짜로 받은 조회 기간을 시각 구간 [시작, 끝) 으로 바꾼 것이다. 둘 다 비어 있으면 기간을 가리지 않는다.
// 장부 내역과 정산 내역이 같은 규칙으로 기간을 받으므로 어느 도메인 것도 아니라 여기 둔다.
public record DatePeriod(Instant startAtInclusive, Instant endAtExclusive) {
    // 장부 · 정산 시각은 서버 시계(ClockConfig, UTC)로 기록되므로 날짜 경계도 같은 기준으로 잡는다.
    public static final ZoneOffset ZONE = ZoneOffset.UTC;

    public DatePeriod {
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

    // 요청 파라미터는 둘 다 없거나 둘 다 있어야 한다. 한쪽만 오면 조회 범위를 정할 수 없다.
    public static DatePeriod of(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return allDates();
        }

        if (startDate == null || endDate == null) {
            throw new CoreException(ErrorType.INVALID_REQUEST,
                    Map.of("from", String.valueOf(startDate), "to", String.valueOf(endDate)));
        }

        return betweenDates(startDate, endDate);
    }

    public static DatePeriod allDates() {
        return new DatePeriod(null, null);
    }

    // 종료일은 그날 하루를 포함하도록 다음 날 0시 미만으로 잡는다.
    public static DatePeriod betweenDates(LocalDate startDate, LocalDate endDate) {
        return new DatePeriod(
                startDate.atStartOfDay(ZONE).toInstant(),
                endDate.plusDays(1).atStartOfDay(ZONE).toInstant()
        );
    }
}
