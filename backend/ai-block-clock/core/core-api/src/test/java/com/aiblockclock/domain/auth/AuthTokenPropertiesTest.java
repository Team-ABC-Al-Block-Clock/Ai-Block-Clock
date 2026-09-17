package com.aiblockclock.domain.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTokenPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesTestConfiguration.class);

    @Test
    void 설정에서_15분과_7일을_읽고_테스트_전용_키를_바인딩한다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            AuthTokenProperties properties = context.getBean(AuthTokenProperties.class);
            assertThat(properties.issuer()).isEqualTo("ai-block-clock");
            assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.sessionTtl()).isEqualTo(Duration.ofDays(7));
            assertThat(Base64.getDecoder().decode(properties.secretKeyBase64())).hasSize(32);
        });
    }

    @Test
    void 외부에서_발급자와_유효_시간을_바꿀_수_있다() {
        contextRunner.withPropertyValues(
                "auth-token.issuer=test-service",
                "auth-token.access-token-ttl=5m",
                "auth-token.session-ttl=2d"
        ).run(context -> {
            assertThat(context).hasNotFailed();

            AuthTokenProperties properties = context.getBean(AuthTokenProperties.class);
            assertThat(properties.issuer()).isEqualTo("test-service");
            assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.sessionTtl()).isEqualTo(Duration.ofDays(2));
        });
    }

    @ParameterizedTest
    @CsvSource({
            "access-token-ttl, 0s, Access Token",
            "access-token-ttl, -1s, Access Token",
            "access-token-ttl, 500ms, Access Token",
            "session-ttl, 0s, 로그인 세션",
            "session-ttl, -1s, 로그인 세션",
            "session-ttl, 1500ms, 로그인 세션"
    })
    void 유효_시간이_양의_정수_초가_아니면_설정을_거절한다(String property, String ttl, String label) {
        contextRunner.withPropertyValues("auth-token." + property + "=" + ttl).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(
                    label + " 유효 시간은 1초 이상의 정수 초여야 합니다."
            );
        });
    }

    @Test
    void 서버_비밀키가_없으면_설정을_거절한다() {
        contextRunner.withPropertyValues("auth-token.secret-key-base64=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("JWT 서명 비밀키를 설정해야 합니다.");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-base64", "YWJj"})
    void Base64가_아니거나_디코딩한_키가_짧으면_설정을_거절한다(String secretKeyBase64) {
        contextRunner.withPropertyValues("auth-token.secret-key-base64=" + secretKeyBase64).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(
                    "JWT 서명 비밀키는 32바이트 이상의 값을 Base64로 인코딩해야 합니다."
            );
        });
    }

    @Test
    void 발급자가_없으면_설정을_거절한다() {
        contextRunner.withPropertyValues("auth-token.issuer=").run(context ->
                assertThat(context).hasFailed());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthTokenProperties.class)
    static class PropertiesTestConfiguration {
    }
}
