package com.aiblockclock.domain.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ChargePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesTestConfiguration.class);

    @Test
    void 기본_설정은_SSAFY_체인과_입금_전용_보관_지갑과_abcToken을_가리킨다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            ChargeProperties properties = context.getBean(ChargeProperties.class);

            assertThat(properties.chainId()).isEqualTo(31221L);
            assertThat(properties.custodyAddress()).isEqualToIgnoringCase("0x64e72a170a7a36ce09806190ed02085fe23cb6b6");
            assertThat(properties.depositTokenContractAddress())
                    .isEqualToIgnoringCase("0x56dc097c14836473f4ad4bd675a3d01be5e4c63c");
            assertThat(properties.requiredConfirmations()).isEqualTo(1L);
            assertThat(properties.pollInterval()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.maxPollInterval()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.verificationTimeout()).isEqualTo(Duration.ofMinutes(30));
        });
    }

    @Test
    void 보관_지갑_주소_형식이_틀리면_기동하지_않는다() {
        contextRunner.withPropertyValues("payment.charge.custody-address=not-an-address")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 토큰_주소가_비어_있으면_기동하지_않는다() {
        contextRunner.withPropertyValues("ssafy.sto.token.contract-address=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 대기_한도가_재확인_간격보다_짧으면_기동하지_않는다() {
        contextRunner.withPropertyValues("payment.charge.verification-timeout=5s")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("충전 확인 대기 한도는 재확인 간격보다 길어야 합니다."));
    }

    @Test
    void 최대_간격이_기본_간격보다_짧으면_기동하지_않는다() {
        contextRunner.withPropertyValues("payment.charge.max-poll-interval=5s")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("충전 재확인 최대 간격은 기본 간격 이상이어야 합니다."));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ChargeProperties.class)
    static class PropertiesTestConfiguration {
    }
}
