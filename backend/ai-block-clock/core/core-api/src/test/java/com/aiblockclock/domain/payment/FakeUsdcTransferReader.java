package com.aiblockclock.domain.payment;

public final class FakeUsdcTransferReader
        implements UsdcTransferReader {

    private OnChainUsdcTransfer result;

    public void willReturn(OnChainUsdcTransfer result) {
        this.result = result;
    }

    @Override
    public OnChainUsdcTransfer read(
            Long chainId,
            String transactionHash
    ) {
        if (result == null) {
            throw new IllegalStateException(
                    "Fake 온체인 조회 결과가 설정되지 않았습니다."
            );
        }

        return result;
    }
}
