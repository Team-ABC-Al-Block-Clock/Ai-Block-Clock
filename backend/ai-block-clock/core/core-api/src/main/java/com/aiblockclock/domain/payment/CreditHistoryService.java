package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.CreditHistoryType;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.support.paging.OffsetCursor;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// 한 사람의 충전과 출금을 한 목록으로 준다(PAY-026). 화면 "코인 예치/출금" 이 두 기록을 한 목록으로 그린다.
// 체인에는 묻지 않는다. 상태와 거래 해시는 충전 확인 · 출금 지급 차례가 이미 적어 둔 값이다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreditHistoryService {

    private final ChargeRequestReader chargeRequestReader;
    private final WithdrawalReader withdrawalReader;

    // 두 표를 한 쿼리로 합치지 않고, 각 표에서 최신순으로 (건너뛸 만큼 + 한 페이지 + 1) 개씩 읽어 합친다.
    // 합친 목록의 앞 N 개는 반드시 각 표의 앞 N 개 안에 있으므로 이것으로 충분하다. 하나 더 읽어 다음 페이지가 있는지 본다.
    // type 을 주면 그 종류만 준다.
    public CursorPage<CreditHistoryItem> read(UserId owner, CreditHistoryType type, String cursor, int size) {
        int offset = OffsetCursor.decode(cursor);
        int limit = (int) Math.min((long) offset + size + 1, Integer.MAX_VALUE);
        List<CreditHistoryItem> items = new ArrayList<>();

        if (type == null || type == CreditHistoryType.CHARGE) {
            chargeRequestReader.readLatest(LedgerTransaction.referenceIdOf(owner), limit)
                    .forEach(charge -> items.add(new CreditHistoryItem.Charge(charge)));
        }

        if (type == null || type == CreditHistoryType.WITHDRAWAL) {
            withdrawalReader.readLatest(owner, limit)
                    .forEach(withdrawal -> items.add(new CreditHistoryItem.Payout(withdrawal)));
        }

        items.sort(CreditHistoryItem.NEWEST_FIRST);

        List<CreditHistoryItem> content = items.stream().skip(offset).limit(size).toList();
        boolean hasNext = items.size() > offset + size;

        return new CursorPage<>(content, hasNext ? OffsetCursor.encode(offset + size) : null, hasNext);
    }
}
