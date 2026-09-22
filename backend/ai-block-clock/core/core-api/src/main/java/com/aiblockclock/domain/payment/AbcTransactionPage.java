package com.aiblockclock.domain.payment;

import java.util.List;

public record AbcTransactionPage(
        List<AbcTransaction> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {

    public AbcTransactionPage {
        items = List.copyOf(items);
    }

    public static AbcTransactionPage of(
            List<AbcTransaction> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasPrevious,
            boolean hasNext
    ) {
        return new AbcTransactionPage(
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
