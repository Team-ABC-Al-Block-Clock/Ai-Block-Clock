package com.aiblockclock.domain.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeNetworkPolicyTest {

    private static final Long CHAIN_ID = 12345L;
    private static final String CUSTODY_ADDRESS =
            "0x2222222222222222222222222222222222222222";
    private static final String USDC_CONTRACT_ADDRESS =
            "0x3333333333333333333333333333333333333333";
    private static final long REQUIRED_CONFIRMATIONS = 5L;

    private final ChargeNetworkPolicy policy =
            new ChargeNetworkPolicy(
                    CHAIN_ID,
                    CUSTODY_ADDRESS,
                    USDC_CONTRACT_ADDRESS,
                    REQUIRED_CONFIRMATIONS
            );

    @Test
    void 같은_체인을_지원한다() {
        assertThat(policy.supportsChain(CHAIN_ID))
                .isTrue();
    }

    @Test
    void 다른_체인을_지원하지_않는다() {
        assertThat(policy.supportsChain(54321L))
                .isFalse();
    }

    @Test
    void 충전_요청과_네트워크_정책으로_검증_명령을_생성한다() {
        ChargeRequest chargeRequest =
                ChargeRequest.pending(
                        "user-request-100",
                        "0x1111111111111111111111111111111111111111",
                        CHAIN_ID,
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        new BigDecimal("10.000000")
                );

        ChargeVerificationCriteria criteria =
                policy.createVerificationCriteria(chargeRequest);

        assertThat(criteria.expectedChainId())
                .isEqualTo(CHAIN_ID);
        assertThat(criteria.expectedTransactionHash())
                .isEqualTo(chargeRequest.getTransactionHash());
        assertThat(criteria.expectedSenderWalletAddress())
                .isEqualTo(chargeRequest.getSenderWalletAddress());
        assertThat(criteria.expectedUsdcAmount())
                .isEqualByComparingTo(
                        chargeRequest.getRequestedUsdcAmount()
                );
        assertThat(criteria.expectedCustodyAddress())
                .isEqualTo(CUSTODY_ADDRESS);
        assertThat(criteria.expectedUsdcContractAddress())
                .isEqualTo(USDC_CONTRACT_ADDRESS);
        assertThat(criteria.requiredConfirmations())
                .isEqualTo(REQUIRED_CONFIRMATIONS);
    }
}
