package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PayoutConversionReader {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    // 같은 요청 번호로 이미 옮겼으면 그때 옮긴 금액이다. 옮기기는 장부 거래 하나뿐이라 따로 표를 두지 않고 거래에서 읽는다.
    @Transactional(readOnly = true)
    public Optional<AbcAmount> findConverted(UserId owner, PayoutConversionRequestId requestId) {
        return ledgerTransactionRepository
                .findByIdempotencyKey(LedgerTransaction.payoutConversionKeyOf(owner, requestId))
                .flatMap(transaction -> ledgerEntryRepository.findAllByTransactionIdOrderByIdAsc(transaction.getId())
                        .stream()
                        .map(LedgerEntryEntity::getAmountDelta)
                        .filter(amount -> amount.signum() > 0)
                        .findFirst())
                .map(AbcAmount::of);
    }
}
