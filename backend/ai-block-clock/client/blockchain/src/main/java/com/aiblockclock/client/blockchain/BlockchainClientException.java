package com.aiblockclock.client.blockchain;

import lombok.Getter;

// 이 모듈이 던지는 유일한 예외다. core-api 의 CoreException 은 역방향 의존이 되어 쓸 수 없다 (MODULES.md 2.3).
@Getter
public class BlockchainClientException extends RuntimeException {
    private final BlockchainClientError error;

    public BlockchainClientException(BlockchainClientError error, String message) {
        this(error, message, null);
    }

    public BlockchainClientException(BlockchainClientError error, String message, Throwable cause) {
        super("[" + error.name() + "] " + message, cause);
        this.error = error;
    }
}
