package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.DatePeriod;
import com.aiblockclock.enums.payment.LedgerEventType;

import java.time.LocalDate;

// 무엇을 볼지(기간·유형)만 담는다. 어디부터 몇 개(cursor·size)는 페이지 넘김 정보라 따로 다닌다.
public record AbcTransactionSearchCondition(
        DatePeriod period,
        LedgerEventType eventType
) {
    public static AbcTransactionSearchCondition of(
            LocalDate startDate,
            LocalDate endDate,
            LedgerEventType eventType
    ) {
        return new AbcTransactionSearchCondition(DatePeriod.of(startDate, endDate), eventType);
    }

    public static AbcTransactionSearchCondition allDates(LedgerEventType eventType) {
        return new AbcTransactionSearchCondition(DatePeriod.allDates(), eventType);
    }

    public static AbcTransactionSearchCondition betweenDates(
            LocalDate startDate,
            LocalDate endDate,
            LedgerEventType eventType
    ) {
        return new AbcTransactionSearchCondition(DatePeriod.betweenDates(startDate, endDate), eventType);
    }
}
