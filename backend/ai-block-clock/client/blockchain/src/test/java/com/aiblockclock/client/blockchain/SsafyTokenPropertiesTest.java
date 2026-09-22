package com.aiblockclock.client.blockchain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsafyTokenPropertiesTest {
    private static final String CONTRACT_ADDRESS = "0x1111111111111111111111111111111111111111";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SsafyStoConfiguration.class)
            .withPropertyValues(
                    "ssafy.sto.base-url=https://sto.ssafy.io",
                    "ssafy.sto.connect-timeout=3s",
                    "ssafy.sto.read-timeout=10s"
            );

    @Test
    void bindsValidContractAddress() {
        contextRunner.withPropertyValues("ssafy.sto.token.contract-address=" + CONTRACT_ADDRESS)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SsafyTokenProperties properties = context.getBean(SsafyTokenProperties.class);
                    assertThat(properties.requiredContractAddress()).isEqualTo(CONTRACT_ADDRESS);
                });
    }

    @Test
    void allowsOrdinaryApplicationToStartWithoutTokenAddress() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SsafyTokenProperties properties = context.getBean(SsafyTokenProperties.class);
            assertThatThrownBy(properties::requiredContractAddress)
                    .isInstanceOfSatisfying(BlockchainClientException.class, exception ->
                            assertThat(exception.getError()).isEqualTo(BlockchainClientError.INVALID_CONFIGURATION));
        });
    }

    @Test
    void refusesMalformedContractAddressAtStartup() {
        contextRunner.withPropertyValues("ssafy.sto.token.contract-address=not-an-address")
                .run(context -> assertThat(context).hasFailed());
    }
}
