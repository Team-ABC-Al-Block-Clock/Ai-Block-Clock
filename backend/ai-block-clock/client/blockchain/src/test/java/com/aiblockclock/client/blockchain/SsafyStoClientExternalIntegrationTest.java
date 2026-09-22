package com.aiblockclock.client.blockchain;

import java.math.BigInteger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "SSAFY_STO_EXTERNAL_TEST", matches = "true")
class SsafyStoClientExternalIntegrationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(SsafyStoConfiguration.class, SsafyStoClient.class);

    @Test
    void readsHealthyStatusFromActualSsafyStoApi() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            SsafyStoClient client = context.getBean(SsafyStoClient.class);
            assertThat(client.isHealthy())
                    .as("Actual SSAFY STO API should report a healthy blockchain")
                    .isTrue();
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(
            named = "SSAFY_TOKEN_CONTRACT_ADDRESS",
            matches = "0x[0-9a-fA-F]{40}"
    )
    void readsDeployedTokenFromActualSsafyStoApi() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            SsafyStoClient client = context.getBean(SsafyStoClient.class);
            String contractAddress = context.getBean(SsafyTokenProperties.class).requiredContractAddress();

            assertThat(client.getTokenName(contractAddress)).isEqualTo("ABC Payment Token");
            assertThat(client.getTokenSymbol(contractAddress)).isEqualTo("abcToken");
            assertThat(client.getGranularity(contractAddress)).isEqualTo(BigInteger.ONE);
            assertThat(client.getTotalSupply(contractAddress)).isNotNegative();
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(
            named = "SSAFY_TOKEN_CONTRACT_ADDRESS",
            matches = "0x[0-9a-fA-F]{40}"
    )
    @EnabledIfEnvironmentVariable(
            named = "SSAFY_TOKEN_OWNER_ADDRESS",
            matches = "0x[0-9a-fA-F]{40}"
    )
    void readsOwnerBalanceAndTokenPermissionsFromActualSsafyStoApi() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            SsafyStoClient client = context.getBean(SsafyStoClient.class);
            String contractAddress = context.getBean(SsafyTokenProperties.class).requiredContractAddress();
            String ownerAddress = System.getenv("SSAFY_TOKEN_OWNER_ADDRESS");

            assertThat(client.getBalance(contractAddress, ownerAddress)).isNotNegative();
            assertThat(client.isMinter(contractAddress, ownerAddress)).isTrue();
            assertThat(client.isIssuable(contractAddress)).isTrue();
        });
    }
}
