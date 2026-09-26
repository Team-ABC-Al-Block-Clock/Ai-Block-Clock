package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PayoutConversionService {

    private final UserReader userReader;
    private final AbcBalanceReader abcBalanceReader;
    private final PayoutConversionReader payoutConversionReader;
    private final LedgerTransactionWriter ledgerTransactionWriter;

    // 정산금을 줄이는 출금 신청과 같은 사용자 행 잠금으로 한 줄로 세운다. 그래야 둘이 겹쳐도 정산금을 넘게 쓰지 않는다.
    // 잠금은 이 트랜잭션의 첫 조회여야 한다 — MySQL 은 첫 일반 조회 때 읽는 기준 시점을 정하므로,
    // 잠금을 얻은 뒤에 읽어야 앞 요청이 커밋한 정산금이 보인다.
    // 같은 요청 번호로 이미 옮겼으면 또 옮기지 않고 그때 옮긴 금액을 돌려준다. 이미 옮겨 줄어든 정산금으로 다시 재지 않는다.
    @Transactional
    public PayoutConversion convert(UserId owner, PayoutConversionRequestId requestId, AbcAmount amount) {
        userReader.findByIdForUpdate(owner)
                .orElseThrow(() -> new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIABLE));
        Optional<AbcAmount> earlier = payoutConversionReader.findConverted(owner, requestId);

        if (earlier.isPresent()) {
            return new PayoutConversion(earlier.get(), abcBalanceReader.read(owner));
        }

        BigDecimal payout = abcBalanceReader.read(owner).payout();

        if (payout.compareTo(amount.value()) < 0) {
            throw new CoreException(ErrorType.PAYOUT_CONVERSION_INSUFFICIENT,
                    Map.of("payoutAmount", payout.toPlainString()));
        }

        ledgerTransactionWriter.append(LedgerTransaction.forPayoutConversion(owner, requestId, amount));

        return new PayoutConversion(amount, abcBalanceReader.read(owner));
    }
}
