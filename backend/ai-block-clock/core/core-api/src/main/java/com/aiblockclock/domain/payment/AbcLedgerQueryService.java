package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.paging.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AbcLedgerQueryService {

    private final AbcBalanceReader abcBalanceReader;
    private final AbcTransactionReader abcTransactionReader;

    public AbcBalance readBalance(UserId userId) {
        return abcBalanceReader.read(userId);
    }

    public CursorPage<AbcTransaction> readTransactions(
            UserId userId,
            AbcTransactionSearchCondition condition,
            String cursor,
            int size
    ) {
        return abcTransactionReader.readPage(referenceIdOf(userId), condition, cursor, size);
    }

    // 장부 계정은 가입 때 LedgerAccountWriter 가 같은 규칙(사용자 ID 의 십진 문자열)으로 만든다.
    private static String referenceIdOf(UserId userId) {
        return Long.toString(userId.value());
    }
}
