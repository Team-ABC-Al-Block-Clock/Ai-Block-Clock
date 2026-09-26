package com.aiblockclock.domain.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WithdrawalPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesTestConfiguration.class);

    @Test
    void 기본_정책은_1에서_1000_ABC까지_24시간에_한_번이다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            WithdrawalProperties properties = context.getBean(WithdrawalProperties.class);

            assertThat(properties.minimumAmount()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(properties.maximumAmount()).isEqualByComparingTo(new BigDecimal("1000"));
            assertThat(properties.requestInterval()).isEqualTo(Duration.ofHours(24));
        });
    }

    @Test
    void 시연처럼_주기를_0으로_두면_기동한다() {
        contextRunner.withPropertyValues("payment.withdrawal.request-interval=0s")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void 최대_금액이_최소_금액보다_작으면_기동하지_않는다() {
        contextRunner.withPropertyValues("payment.withdrawal.maximum-amount=0.5")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("출금 최대 금액은 최소 금액 이상이어야 합니다."));
    }

    @Test
    void 신청_주기가_음수면_기동하지_않는다() {
        contextRunner.withPropertyValues("payment.withdrawal.request-interval=-1h")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("출금 신청 주기는 0 이상이어야 합니다."));
    }

    @Test
    void 최소_금액이_0이면_기동하지_않는다() {
        contextRunner.withPropertyValues("payment.withdrawal.minimum-amount=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 금액이_소수_6자리를_넘으면_기동하지_않는다() {
        contextRunner.withPropertyValues("payment.withdrawal.minimum-amount=0.0000001")
                .run(context -> assertThat(context).hasFailed());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WithdrawalProperties.class)
    static class PropertiesTestConfiguration {
    }
}
