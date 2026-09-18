package com.aiblockclock.support.blockchain;

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
}
