package com.aiblockclock.domain.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OnChainUsdcTransferTest {

    @Test
    void 온체인_전송값을_검증_기준과_비교한다() {
        OnChainUsdcTransfer transfer = successfulTransfer(
                new BigDecimal("10.000000"),
                5L
        );

        assertThat(transfer.matchesChainId(12345L)).isTrue();
        assertThat(transfer.matchesTransactionHash(
                "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        )).isTrue();
        assertThat(transfer.matchesSenderWalletAddress(
                "0x1111111111111111111111111111111111111111"
        )).isTrue();
        assertThat(transfer.matchesRecipientAddress(
                "0x2222222222222222222222222222222222222222"
        )).isTrue();
        assertThat(transfer.matchesTokenContractAddress(
                "0x3333333333333333333333333333333333333333"
        )).isTrue();
        assertThat(transfer.matchesAmount(
                new BigDecimal("10.0")
        )).isTrue();
        assertThat(transfer.hasRequiredConfirmations(5L)).isTrue();
    }

    @Test
    void 온체인_전송값이_검증_기준과_다르면_일치하지_않는다() {
        OnChainUsdcTransfer transfer = successfulTransfer(
                new BigDecimal("10.000000"),
                4L
        );

        assertThat(transfer.matchesChainId(99999L)).isFalse();
        assertThat(transfer.matchesTransactionHash("0xdifferent")).isFalse();
        assertThat(transfer.matchesAmount(new BigDecimal("9.000000")))
                .isFalse();
        assertThat(transfer.hasRequiredConfirmations(5L)).isFalse();
    }

    @Test
    void 조회되지_않았거나_처리_중인_거래는_확정을_기다린다() {
        OnChainUsdcTransfer notFound = transferWithStatus(
                OnChainTransactionStatus.NOT_FOUND
        );
        OnChainUsdcTransfer pending = transferWithStatus(
                OnChainTransactionStatus.PENDING
        );

        assertThat(notFound.isWaitingForConfirmation()).isTrue();
        assertThat(pending.isWaitingForConfirmation()).isTrue();
    }

    @Test
    void 실패한_온체인_거래를_판단한다() {
        OnChainUsdcTransfer failed = transferWithStatus(
                OnChainTransactionStatus.FAILED
        );

        assertThat(failed.isFailed()).isTrue();
    }

    private OnChainUsdcTransfer successfulTransfer(
            BigDecimal amount,
            long confirmations
    ) {
        return new OnChainUsdcTransfer(
                OnChainTransactionStatus.SUCCESS,
                12345L,
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "0x1111111111111111111111111111111111111111",
                "0x2222222222222222222222222222222222222222",
                "0x3333333333333333333333333333333333333333",
                amount,
                confirmations
        );
    }

    private OnChainUsdcTransfer transferWithStatus(
            OnChainTransactionStatus status
    ) {
        return new OnChainUsdcTransfer(
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                0L
        );
    }
}
