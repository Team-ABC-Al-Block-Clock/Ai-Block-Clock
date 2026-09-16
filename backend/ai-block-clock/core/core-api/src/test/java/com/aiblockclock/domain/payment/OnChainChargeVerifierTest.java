package com.aiblockclock.domain.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiblockclock.enums.payment.ChargeStatusReason;

class OnChainChargeVerifierTest {

    @Test
    void 온체인_거래를_찾지_못하면_WAITING을_반환한다() {
        ChargeVerificationCriteria criteria =
                criteria("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        OnChainUsdcTransfer transfer =
                new OnChainUsdcTransfer(
                        OnChainTransactionStatus.NOT_FOUND,
                        12345L,
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        null,
                        null,
                        null,
                        null,
                        0L
                );

        ChargeVerificationResult result =
                new OnChainChargeVerifier().verify(criteria, transfer);

        assertThat(result.outcome())
                .isEqualTo(ChargeVerificationOutcome.WAITING);
        assertThat(result.reason())
                .isNull();
    }

    @Test
    void 온체인_거래가_처리_중이면_WAITING을_반환한다() {
        ChargeVerificationCriteria criteria =
                criteria("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        OnChainUsdcTransfer transfer =
                new OnChainUsdcTransfer(
                        OnChainTransactionStatus.PENDING,
                        12345L,
                        "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        null,
                        null,
                        null,
                        null,
                        0L
                );

        ChargeVerificationResult result =
                new OnChainChargeVerifier().verify(criteria, transfer);

        assertThat(result.outcome())
                .isEqualTo(ChargeVerificationOutcome.WAITING);
        assertThat(result.reason())
                .isNull();
    }

    @Test
    void 온체인_거래가_실패하면_REJECTED를_반환한다() {
        ChargeVerificationCriteria criteria =
                criteria("0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");

        OnChainUsdcTransfer transfer =
                new OnChainUsdcTransfer(
                        OnChainTransactionStatus.FAILED,
                        12345L,
                        "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                        null,
                        null,
                        null,
                        null,
                        0L
                );

        ChargeVerificationResult result =
                new OnChainChargeVerifier().verify(criteria, transfer);

        assertThat(result.outcome())
                .isEqualTo(ChargeVerificationOutcome.REJECTED);

        assertThat(result.reason())
                .isEqualTo(
                        ChargeStatusReason.ONCHAIN_TRANSACTION_FAILED
                );
    }

    @Test
    void 온체인_거래는_성공했지만_확인_블록이_부족하면_WAITING을_반환한다() {
        ChargeVerificationCriteria criteria =
                criteria("0xdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd");

        OnChainUsdcTransfer transfer =
                new OnChainUsdcTransfer(
                        OnChainTransactionStatus.SUCCESS,
                        12345L,
                        "0xdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                        "0x1111111111111111111111111111111111111111",
                        "0x2222222222222222222222222222222222222222",
                        "0x3333333333333333333333333333333333333333",
                        new BigDecimal("10.000000"),
                        3L
                );

        ChargeVerificationResult result =
                new OnChainChargeVerifier().verify(criteria, transfer);

        assertThat(result.outcome())
                .isEqualTo(ChargeVerificationOutcome.WAITING);

        assertThat(result.reason())
                .isNull();
    }

    @Test
    void 체인_ID가_다르면_REJECTED를_반환한다() {
        ChargeVerificationCriteria criteria =
                criteria("0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");

        OnChainUsdcTransfer transfer =
                new OnChainUsdcTransfer(
                        OnChainTransactionStatus.SUCCESS,
                        99999L,
                        "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                        "0x1111111111111111111111111111111111111111",
                        "0x2222222222222222222222222222222222222222",
                        "0x3333333333333333333333333333333333333333",
                        new BigDecimal("10.000000"),
                        5L
                );

        ChargeVerificationResult result =
                new OnChainChargeVerifier().verify(criteria, transfer);

        assertThat(result.outcome())
                .isEqualTo(ChargeVerificationOutcome.REJECTED);

        assertThat(result.reason())
                .isEqualTo(ChargeStatusReason.CHAIN_ID_MISMATCH);
    }

    @Test
    void 거래_해시가_다르면_REJECTED를_반환한다() {
        ChargeVerificationCriteria criteria =
                criteria("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

        OnChainUsdcTransfer transfer =
                new OnChainUsdcTransfer(
                        OnChainTransactionStatus.SUCCESS,
                        12345L,
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "0x1111111111111111111111111111111111111111",
                        "0x2222222222222222222222222222222222222222",
                        "0x3333333333333333333333333333333333333333",
                        new BigDecimal("10.000000"),
                        5L
                );

        ChargeVerificationResult result =
                new OnChainChargeVerifier().verify(criteria, transfer);

        assertThat(result.outcome())
                .isEqualTo(ChargeVerificationOutcome.REJECTED);

        assertThat(result.reason())
                .isEqualTo(
                        ChargeStatusReason.TRANSACTION_HASH_MISMATCH
                );
    }

    @Test
    void 보낸_사람의_지갑_주소가_다르면_REJECTED를_반환한다() {
        ChargeVerificationCriteria criteria =
                criteria("0x4444444444444444444444444444444444444444444444444444444444444444");

        OnChainUsdcTransfer transfer =
                new OnChainUsdcTransfer(
                        OnChainTransactionStatus.SUCCESS,
                        12345L,
                        "0x4444444444444444444444444444444444444444444444444444444444444444",
                        "0x9999999999999999999999999999999999999999",
                        "0x2222222222222222222222222222222222222222",
                        "0x3333333333333333333333333333333333333333",
                        new BigDecimal("10.000000"),
                        5L
                );

        ChargeVerificationResult result =
                new OnChainChargeVerifier().verify(criteria, transfer);

        assertThat(result.outcome())
                .isEqualTo(ChargeVerificationOutcome.REJECTED);

        assertThat(result.reason())
                .isEqualTo(
                        ChargeStatusReason.SENDER_WALLET_MISMATCH
                );
    }

    @Test
    void USDC_수신_주소가_커스터디_주소와_다르면_REJECTED를_반환한다() {
        ChargeVerificationCriteria criteria =
                criteria("0x5555555555555555555555555555555555555555555555555555555555555555");

        OnChainUsdcTransfer transfer =
                new OnChainUsdcTransfer(
                        OnChainTransactionStatus.SUCCESS,
                        12345L,
                        "0x5555555555555555555555555555555555555555555555555555555555555555",
                        "0x1111111111111111111111111111111111111111",
                        "0x8888888888888888888888888888888888888888",
                        "0x3333333333333333333333333333333333333333",
                        new BigDecimal("10.000000"),
                        5L
                );

        ChargeVerificationResult result =
                new OnChainChargeVerifier().verify(criteria, transfer);

        assertThat(result.outcome())
                .isEqualTo(ChargeVerificationOutcome.REJECTED);

        assertThat(result.reason())
                .isEqualTo(
                        ChargeStatusReason.CUSTODY_ADDRESS_MISMATCH
                );
    }

    @Test
    void USDC_컨트랙트_주소가_다르면_REJECTED를_반환한다() {
        ChargeVerificationCriteria criteria =
                criteria("0x6666666666666666666666666666666666666666666666666666666666666666");

        OnChainUsdcTransfer transfer =
                new OnChainUsdcTransfer(
                        OnChainTransactionStatus.SUCCESS,
                        12345L,
                        "0x6666666666666666666666666666666666666666666666666666666666666666",
                        "0x1111111111111111111111111111111111111111",
                        "0x2222222222222222222222222222222222222222",
                        "0x7777777777777777777777777777777777777777",
                        new BigDecimal("10.000000"),
                        5L
                );

        ChargeVerificationResult result =
                new OnChainChargeVerifier().verify(criteria, transfer);

        assertThat(result.outcome())
                .isEqualTo(ChargeVerificationOutcome.REJECTED);

        assertThat(result.reason())
                .isEqualTo(
                        ChargeStatusReason.USDC_CONTRACT_MISMATCH
                );
    }

    @Test
    void 실제_USDC_전송_금액이_다르면_REJECTED를_반환한다() {
        ChargeVerificationCriteria criteria =
                criteria("0x7777777777777777777777777777777777777777777777777777777777777777");

        OnChainUsdcTransfer transfer =
                new OnChainUsdcTransfer(
                        OnChainTransactionStatus.SUCCESS,
                        12345L,
                        "0x7777777777777777777777777777777777777777777777777777777777777777",
                        "0x1111111111111111111111111111111111111111",
                        "0x2222222222222222222222222222222222222222",
                        "0x3333333333333333333333333333333333333333",
                        new BigDecimal("9.000000"),
                        5L
                );

        ChargeVerificationResult result =
                new OnChainChargeVerifier().verify(criteria, transfer);

        assertThat(result.outcome())
                .isEqualTo(ChargeVerificationOutcome.REJECTED);

        assertThat(result.reason())
                .isEqualTo(ChargeStatusReason.AMOUNT_MISMATCH);
    }

    @Test
    void 모든_충전_조건을_충족하면_VERIFIED를_반환한다() {
        ChargeVerificationCriteria criteria =
                criteria("0x8888888888888888888888888888888888888888888888888888888888888888");

        OnChainUsdcTransfer transfer =
                new OnChainUsdcTransfer(
                        OnChainTransactionStatus.SUCCESS,
                        12345L,
                        "0x8888888888888888888888888888888888888888888888888888888888888888",
                        "0x1111111111111111111111111111111111111111",
                        "0x2222222222222222222222222222222222222222",
                        "0x3333333333333333333333333333333333333333",
                        new BigDecimal("10.0"),
                        5L
                );

        ChargeVerificationResult result =
                new OnChainChargeVerifier().verify(criteria, transfer);

        assertThat(result.outcome())
                .isEqualTo(ChargeVerificationOutcome.VERIFIED);

        assertThat(result.reason())
                .isNull();
    }

    private ChargeVerificationCriteria criteria(
            String transactionHash
    ) {
        return new ChargeVerificationCriteria(
                12345L,
                transactionHash,
                "0x1111111111111111111111111111111111111111",
                DepositAmount.of(new BigDecimal("10.000000")),
                "0x2222222222222222222222222222222222222222",
                "0x3333333333333333333333333333333333333333",
                5L
        );
    }
}
