package com.aiblockclock.client.blockchain;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SsafyStoPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SsafyStoConfiguration.class)
            .withPropertyValues(
                    "ssafy.sto.base-url=https://sto.ssafy.io",
                    "ssafy.sto.connect-timeout=3s",
                    "ssafy.sto.read-timeout=10s",
                    "ssafy.sto.transaction-timeout=60s"
            );

    @Test
    void bindsServerAddressAndTimeouts() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SsafyStoProperties properties = context.getBean(SsafyStoProperties.class);
            assertThat(properties.baseUrl()).isEqualTo(URI.create("https://sto.ssafy.io"));
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.transactionTimeout()).isEqualTo(Duration.ofSeconds(60));
        });
    }

    @Test
    void refusesInvalidServerAddressAtStartup() {
        contextRunner.withPropertyValues("ssafy.sto.base-url=ftp://sto.ssafy.io")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesZeroTimeoutAtStartup() {
        contextRunner.withPropertyValues("ssafy.sto.read-timeout=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesNegativeTimeoutAtStartup() {
        contextRunner.withPropertyValues("ssafy.sto.connect-timeout=-1s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesTimeoutOutsideClientSupportedRangeAtStartup() {
        contextRunner.withPropertyValues("ssafy.sto.read-timeout=30d")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesZeroTransactionTimeoutAtStartup() {
        contextRunner.withPropertyValues("ssafy.sto.transaction-timeout=0s")
                .run(context -> assertThat(context).hasFailed());
    }
}
