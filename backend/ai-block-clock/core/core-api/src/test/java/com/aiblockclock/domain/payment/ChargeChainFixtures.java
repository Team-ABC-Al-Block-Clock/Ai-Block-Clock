package com.aiblockclock.domain.payment;

import com.aiblockclock.client.blockchain.ChainTokenTransfer;
import com.aiblockclock.client.blockchain.ChainTransaction;
import com.aiblockclock.client.blockchain.ChainTransactionStatus;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

// 충전 통합 테스트가 쓰는 체인 설정과 체인 답이다. SsafyChainClient 를 목으로 바꾸고 이 값을 돌려준다.
final class ChargeChainFixtures {
    static final long CHAIN_ID = 12345L;
    static final String CUSTODY = "0x2222222222222222222222222222222222222222";
    static final String TOKEN = "0x3333333333333333333333333333333333333333";

    static final String CHAIN_ID_PROPERTY = "payment.charge.chain-id=12345";
    static final String CUSTODY_PROPERTY = "payment.charge.custody-address=" + CUSTODY;
    static final String TOKEN_PROPERTY = "payment.charge.deposit-token-contract-address=" + TOKEN;
    static final String CONFIRMATIONS_PROPERTY = "payment.charge.required-confirmations=5";

    private static final BigInteger BLOCK = BigInteger.valueOf(100);
    private static final BigInteger BLOCK_AFTER_FIVE = BigInteger.valueOf(104);

    private ChargeChainFixtures() {
    }

    // 보관 지갑으로 abcToken 을 보낸 거래가 블록에 적혔고 확인 블록 5개가 쌓였다
    static ChainTransaction deposited(String sender, BigDecimal tokens) {
        return new ChainTransaction(ChainTransactionStatus.SUCCEEDED, CHAIN_ID, BLOCK, BLOCK_AFTER_FIVE,
                List.of(new ChainTokenTransfer(TOKEN, sender, CUSTODY, raw(tokens))));
    }

    static ChainTransaction reverted() {
        return new ChainTransaction(ChainTransactionStatus.FAILED, CHAIN_ID, BLOCK, BLOCK_AFTER_FIVE, List.of());
    }

    static ChainTransaction notYetInBlock() {
        return new ChainTransaction(ChainTransactionStatus.PENDING, CHAIN_ID, null, null, List.of());
    }

    static BigInteger raw(BigDecimal tokens) {
        return tokens.movePointRight(18).toBigIntegerExact();
    }
}
