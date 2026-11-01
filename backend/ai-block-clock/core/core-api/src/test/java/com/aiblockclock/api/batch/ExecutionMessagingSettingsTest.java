package com.aiblockclock.api.batch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// 실행 발행 · 감시의 기본값이다. 운영 환경 변수에 기대지 않도록 시스템 값은 지우고 application.yaml 만 읽는다.
class ExecutionMessagingSettingsTest {
    private final StandardEnvironment environment = applicationYaml();

    @Test
    void 넘기지_않은_실행의_발행은_기본_5초마다_다시_시도한다() {
        assertThat(durationOf("execution.dispatch.interval")).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void 접수하고_기본_10분이_지나도록_넘기지_못한_실행은_버린다() {
        assertThat(durationOf("execution.dispatch.abandon-after")).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void 감시는_기본_10초마다_점검한다() {
        assertThat(durationOf("execution.watch.interval")).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void 감시_기한은_폴링_시절의_단계당_60초와_grace_60초를_그대로_쓴다() {
        assertThat(durationOf("execution.polling.per-step")).isEqualTo(Duration.ofSeconds(60));
        assertThat(durationOf("execution.polling.grace")).isEqualTo(Duration.ofSeconds(60));
    }

    // B13 — 넘긴 뒤 큐 대기 한도(10분)에 더해 dead letter 를 기다려 주는 유예다. 넘긴 지 15분이면 안전망이 닫는다.
    @Test
    void 큐_대기_안전망의_유예는_기본_5분이다() {
        assertThat(durationOf("execution.recovery.queue-grace")).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void 실행마다_2초_폴링하던_간격과_스레드_수_설정은_없다() {
        assertThat(environment.containsProperty("execution.polling.interval")).isFalse();
        assertThat(environment.containsProperty("execution.polling.workers")).isFalse();
    }

    private Duration durationOf(String key) {
        String value = environment.getProperty(key);

        assertThat(value).as(key).isNotNull();

        return DurationStyle.detectAndParse(value);
    }

    private static StandardEnvironment applicationYaml() {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();

        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);

        try {
            new YamlPropertySourceLoader().load("application.yaml", new ClassPathResource("application.yaml"))
                    .forEach(sources::addLast);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return environment;
    }
}
