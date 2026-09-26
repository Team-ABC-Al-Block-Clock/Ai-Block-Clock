package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.CreditHistoryType;

import java.time.Instant;
import java.util.Comparator;

// 충전 · 출금 내역 한 줄이다. 화면이 두 목록을 한 목록으로 보여 주므로 두 기록을 같은 자리에 세운다.
public sealed interface CreditHistoryItem {

    // 최신 요청이 먼저다. 같은 시각이면 종류 · 번호로 순서를 고정해 페이지를 넘겨도 줄이 섞이지 않는다
    Comparator<CreditHistoryItem> NEWEST_FIRST = Comparator.comparing(CreditHistoryItem::requestedAt).reversed()
            .thenComparing(CreditHistoryItem::type)
            .thenComparing(Comparator.comparing(CreditHistoryItem::id).reversed());

    CreditHistoryType type();

    Long id();

    Instant requestedAt();

    record Charge(ChargeRequest request) implements CreditHistoryItem {

        @Override
        public CreditHistoryType type() {
            return CreditHistoryType.CHARGE;
        }

        @Override
        public Long id() {
            return request.getId();
        }

        @Override
        public Instant requestedAt() {
            return request.getRequestedAt();
        }
    }

    record Payout(Withdrawal withdrawal) implements CreditHistoryItem {

        @Override
        public CreditHistoryType type() {
            return CreditHistoryType.WITHDRAWAL;
        }

        @Override
        public Long id() {
            return withdrawal.getId();
        }

        @Override
        public Instant requestedAt() {
            return withdrawal.getRequestedAt();
        }
    }
}
