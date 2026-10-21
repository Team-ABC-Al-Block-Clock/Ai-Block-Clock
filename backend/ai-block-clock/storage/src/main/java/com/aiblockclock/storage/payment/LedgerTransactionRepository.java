package com.aiblockclock.storage.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerTransactionRepository
        extends JpaRepository<LedgerTransactionEntity, Long> {

    Optional<LedgerTransactionEntity> findByIdempotencyKey(
            String idempotencyKey
    );

    Optional<LedgerTransactionEntity> findByChainIdAndTransactionHash(
            Long chainId,
            String transactionHash
    );

    // 한 실행(참조)에 걸린 장부 거래 전부다. 선차감 · 되돌리기 · 환불 · 정산이 나온 순서대로다.
    List<LedgerTransactionEntity> findAllByReferenceTypeAndReferenceIdOrderByIdAsc(
            String referenceType,
            String referenceId
    );
}
