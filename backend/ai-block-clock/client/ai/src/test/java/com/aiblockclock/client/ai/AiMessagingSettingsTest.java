package com.aiblockclock.client.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 브로커 접속 · confirm · 리스너 재시도 · 결과 볼륨 설정은 :client:ai 의 ai.yml 이 갖는다.
// 환경 변수 이름은 런타임과 같다 (contract 10장). 운영 환경 변수에 기대지 않도록 시스템 값은 지우고 읽는다.
class AiMessagingSettingsTest {

    @Test
    void 브로커_접속은_계약의_환경_변수로_받는다() {
        StandardEnvironment environment = environmentWith(Map.of(
                "RABBITMQ_HOST", "abc-rabbitmq",
                "RABBITMQ_PORT", "5673",
                "RABBITMQ_USERNAME", "abc",
                "RABBITMQ_PASSWORD", "secret",
                "RABBITMQ_VHOST", "abc"));

        assertThat(environment.getProperty("spring.rabbitmq.host")).isEqualTo("abc-rabbitmq");
        assertThat(environment.getProperty("spring.rabbitmq.port")).isEqualTo("5673");
        assertThat(environment.getProperty("spring.rabbitmq.username")).isEqualTo("abc");
        assertThat(environment.getProperty("spring.rabbitmq.password")).isEqualTo("secret");
        assertThat(environment.getProperty("spring.rabbitmq.virtual-host")).isEqualTo("abc");
    }

    @Test
    void 환경_변수가_없으면_로컬_브로커의_기본값으로_붙는다() {
        StandardEnvironment environment = environmentWith(Map.of());

        assertThat(environment.getProperty("spring.rabbitmq.host")).isEqualTo("localhost");
        assertThat(environment.getProperty("spring.rabbitmq.port")).isEqualTo("5672");
        assertThat(environment.getProperty("spring.rabbitmq.username")).isEqualTo("guest");
        assertThat(environment.getProperty("spring.rabbitmq.password")).isEqualTo("guest");
        assertThat(environment.getProperty("spring.rabbitmq.virtual-host")).isEqualTo("/");
    }

    @Test
    void 발행은_publisher_confirm_을_받고_기본_5초까지_기다린다() {
        StandardEnvironment environment = environmentWith(Map.of());

        assertThat(environment.getProperty("spring.rabbitmq.publisher-confirm-type")).isEqualToIgnoringCase("correlated");
        assertThat(DurationStyle.detectAndParse(environment.getProperty("ai.messaging.confirm-timeout")))
                .isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void 리스너는_처리에_실패하면_세_번_더_시도하고_그_뒤에는_다시_넣지_않는다() {
        StandardEnvironment environment = environmentWith(Map.of());

        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.max-retries")).isEqualTo("3");
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.default-requeue-rejected"))
                .isEqualTo("false");
    }

    @Test
    void 결과_볼륨_루트는_RESULTS_ROOT_로_받고_없으면_기본_위치다() {
        assertThat(environmentWith(Map.of("RESULTS_ROOT", "/mnt/results")).getProperty("ai.runtime.results-root"))
                .isEqualTo("/mnt/results");
        assertThat(environmentWith(Map.of()).getProperty("ai.runtime.results-root"))
                .isEqualTo("/var/lib/ai-block-clock/results");
    }

    private static StandardEnvironment environmentWith(Map<String, Object> variables) {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();

        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new MapPropertySource("variables", variables));

        try {
            new YamlPropertySourceLoader().load("ai.yml", new ClassPathResource("ai.yml")).forEach(sources::addLast);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return environment;
    }
}
