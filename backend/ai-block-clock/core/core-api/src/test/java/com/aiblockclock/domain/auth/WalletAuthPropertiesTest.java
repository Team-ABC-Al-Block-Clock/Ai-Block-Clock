package com.aiblockclock.domain.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WalletAuthPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesTestConfiguration.class);

    @Test
    @DisplayName("로컬 환경은 개발 사이트 주소와 공통 인증 설정을 읽는다")
    void bindLocalSettings() {
        contextRunner.withPropertyValues("spring.profiles.active=local").run(context -> {
            assertThat(context).hasNotFailed();

            WalletAuthProperties properties = context.getBean(WalletAuthProperties.class);
            assertThat(properties.domain()).isEqualTo("localhost:5173");
            assertThat(properties.uri()).isEqualTo(URI.create("http://localhost:5173/"));
            assertThat(properties.chainId()).isEqualTo(80002L);
            assertThat(properties.messageTtl()).isEqualTo(Duration.ofMinutes(5));
        });
    }

    @Test
    @DisplayName("배포 환경은 실제 서비스 주소를 읽는다")
    void bindProductionSettings() {
        contextRunner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThat(context).hasNotFailed();

            WalletAuthProperties properties = context.getBean(WalletAuthProperties.class);
            assertThat(properties.domain()).isEqualTo("j15a403.p.ssafy.io");
            assertThat(properties.uri()).isEqualTo(URI.create("https://j15a403.p.ssafy.io/"));
        });
    }

    @Test
    @DisplayName("유효 시간이 0 또는 음수이면 인증 설정을 등록하지 않는다")
    void rejectNonPositiveMessageTtl() {
        for (String invalidTtl : new String[]{"0s", "-1s"}) {
            contextRunner.withPropertyValues(
                    "spring.profiles.active=local",
                    "wallet-auth.message-ttl=" + invalidTtl
            ).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("지갑 인증 메시지 유효 시간은 0보다 커야 합니다.");
            });
        }
    }

    @Test
    void 설정한_체인_ID로_네트워크_이름을_찾는다() {
        contextRunner.withPropertyValues("spring.profiles.active=local").run(context -> {
            WalletAuthProperties properties = context.getBean(WalletAuthProperties.class);

            assertThat(properties.network()).isEqualTo(WalletNetwork.POLYGON_AMOY);
        });
    }

    @Test
    void 지원하지_않는_체인_ID이면_인증_설정을_등록하지_않는다() {
        contextRunner.withPropertyValues(
                "spring.profiles.active=local",
                "wallet-auth.chain-id=137"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("지갑 인증 체인 ID 는 지원하는 네트워크여야 합니다.");
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WalletAuthProperties.class)
    static class PropertiesTestConfiguration {
    }
}
