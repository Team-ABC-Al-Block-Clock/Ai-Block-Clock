package com.aiblockclock.domain.payment;

public interface UsdcTransferReader {

    OnChainUsdcTransfer read(
            Long chainId,
            String transactionHash
    );
}
