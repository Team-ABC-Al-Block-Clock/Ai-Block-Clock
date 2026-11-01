package com.aiblockclock.client.ai;

import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AiProperties.class, RecommendProperties.class, RuntimeProperties.class,
        MessagingProperties.class})
public class AiClientConfiguration {
    // RestClient 는 빈으로 내놓지 않는다. 다른 어댑터도 제 것을 내놓고 있어,
    // 타입으로 주입받는 쪽이 어느 것을 받을지 알 수 없게 된다. 클라이언트마다 제 것을 들고 있는다.
    @Bean
    public RecommendClient recommendClient(RecommendProperties properties, AiProperties shared) {
        return new RecommendClient(AiEndpoint.restClient(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout(), shared));
    }

    @Bean
    public AiRuntimeClient aiRuntimeClient(RuntimeProperties properties, AiProperties shared) {
        warnIfNoServiceToken(shared);

        return new AiRuntimeClient(AiEndpoint.restClient(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout(), shared));
    }

    @Bean
    public RunQueuePublisher runQueuePublisher(RabbitTemplate rabbitTemplate, MessagingProperties messaging) {
        return new RunQueuePublisher(rabbitTemplate, messaging.confirmTimeout());
    }

    @Bean
    public RunResultVolume runResultVolume(RuntimeProperties properties) {
        return new RunResultVolume(properties.resultsRoot());
    }

    // 브로커에 처음 붙을 때 RabbitAdmin 이 선언한다. 브로커가 없어도 기동은 막지 않고, 붙는 순간 선언한다.
    @Bean
    public Declarables runTopology() {
        return RunTopology.declarables();
    }

    // 리스너가 거절(AmqpRejectAndDontRequeueException)한 메시지는 다시 시도해도 읽히지 않는다. 곧바로 DLQ 로 보낸다.
    // 그 밖의 처리 실패는 ai.yml 의 재시도 설정대로 다시 시도한다.
    @Bean
    public RabbitListenerRetrySettingsCustomizer listenerRetrySettingsCustomizer() {
        return settings -> settings.setExceptionExcludes(List.of(AmqpRejectAndDontRequeueException.class));
    }

    // 토큰은 두 서버가 나눠 갖는 비밀값이다. 비어 있으면 헤더를 붙이지 않는다 —
    // AI 파트도 값이 없으면 검사하지 않으므로 로컬에서는 설정 없이 붙는다.
    // 빠뜨렸다고 기동을 막지는 않고, 알아챌 수 있게 경고만 남긴다. 토큰은 하나라 한 번만 남긴다.
    private static void warnIfNoServiceToken(AiProperties shared) {
        if (!shared.hasServiceToken()) {
            log.warn("ai.service-token 이 비어 있어 AI 파트 서비스에 서비스 토큰 없이 붙는다");
        }
    }
}
