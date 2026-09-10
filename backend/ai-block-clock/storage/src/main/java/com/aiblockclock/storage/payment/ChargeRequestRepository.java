package com.aiblockclock.storage.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChargeRequestRepository
        extends JpaRepository<ChargeRequestEntity, Long> {

    Optional<ChargeRequestEntity> findByChainIdAndTransactionHash(
            Long chainId,
            String transactionHash
    );
}
