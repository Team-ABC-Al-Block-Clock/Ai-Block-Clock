package com.aiblockclock.client.blockchain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "SSAFY_CHAIN_EXTERNAL_TEST", matches = "true")
class SsafyChainClientExternalIntegrationTest {
    // 2026-09-25 블록 11,365,546 에 실제로 적힌 거래다. 체인이 지워지지 않는 한 늘 같은 답이 나온다
    private static final String MINED_TRANSACTION =
            "0x66c02aecb4cee86cd41e000bf73ef8e24a909052874714598c46f753df011441";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.config.name=blockchain")
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(SsafyChainConfiguration.class, SsafyChainClient.class);

    @Test
    void 실제_SSAFY_체인에서_적힌_거래를_읽는다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            ChainTransaction transaction = context.getBean(SsafyChainClient.class).readTransaction(MINED_TRANSACTION);

            assertThat(transaction.status()).isIn(ChainTransactionStatus.SUCCEEDED, ChainTransactionStatus.FAILED);
            assertThat(transaction.chainId()).isEqualTo(31221L);
            assertThat(transaction.latestBlockNumber()).isGreaterThanOrEqualTo(transaction.blockNumber());
        });
    }

    @Test
    void 실제_SSAFY_체인에_없는_해시는_찾지_못한다() {
        contextRunner.run(context -> {
            ChainTransaction transaction = context.getBean(SsafyChainClient.class).readTransaction("0x" + "0".repeat(64));

            assertThat(transaction.status()).isEqualTo(ChainTransactionStatus.NOT_FOUND);
        });
    }
}
