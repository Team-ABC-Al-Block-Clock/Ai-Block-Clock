package com.aiblockclock.domain.payment;

import java.util.List;

public record CreditTransactionPage(
        List<CreditTransaction> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {

    public CreditTransactionPage {
        items = List.copyOf(items);
    }

    public static CreditTransactionPage of(
            List<CreditTransaction> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasPrevious,
            boolean hasNext
    ) {
        return new CreditTransactionPage(
                items,
                page,
                size,
                totalElements,
                totalPages,
                hasPrevious,
                hasNext
        );
    }
}
