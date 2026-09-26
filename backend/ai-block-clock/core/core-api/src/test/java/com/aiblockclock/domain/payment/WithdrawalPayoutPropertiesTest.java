package com.aiblockclock.domain.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WithdrawalPayoutPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesTestConfiguration.class);

    @Test
    void 기본값은_꺼져_있고_10초마다_돌며_10분_동안_결과를_모르면_보류한다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            WithdrawalPayoutProperties properties = context.getBean(WithdrawalPayoutProperties.class);

            assertThat(properties.enabled()).isFalse();
            assertThat(properties.pollInterval()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.holdAfter()).isEqualTo(Duration.ofMinutes(10));
        });
    }

    @Test
    void 보류_기준이_지급_간격보다_길지_않으면_기동하지_않는다() {
        contextRunner.withPropertyValues("payment.withdrawal.payout.hold-after=10s")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("출금 보류 기준은 지급 간격보다 길어야 합니다."));
    }

    @Test
    void 지급_간격이_0이면_기동하지_않는다() {
        contextRunner.withPropertyValues("payment.withdrawal.payout.poll-interval=0s")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("출금 지급 간격은 0보다 커야 합니다."));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WithdrawalPayoutProperties.class)
    static class PropertiesTestConfiguration {
    }
}
