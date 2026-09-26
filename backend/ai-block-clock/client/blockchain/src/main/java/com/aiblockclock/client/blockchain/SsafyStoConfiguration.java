package com.aiblockclock.client.blockchain;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SsafyStoProperties.class, SsafyTokenProperties.class,
        SsafyAgentRegistryProperties.class})
public class SsafyStoConfiguration {
    // 토큰 배포 · 발행은 운영자가 스크립트로 한다. 토큰증권 API 에 애플리케이션이 쓰는 것은 에이전트 등기부의 문서 칸뿐이다.
    @Bean
    public RestClient ssafyStoQueryRestClient(SsafyStoProperties properties) {
        return restClient(properties, properties.readTimeout());
    }

    // RestClient 를 빈으로 더 내놓지 않는다. 조회용 빈이 이미 있어, 타입으로 주입받는 쪽이 어느 것을 받을지 알 수 없게 된다.
    // 쓰기는 거래가 블록에 적힐 때까지 기다리므로 조회보다 긴 제한 시간을 따로 둔다.
    @Bean
    public SsafyStoDocumentClient ssafyStoDocumentClient(
            SsafyStoProperties properties, SsafyAgentRegistryProperties registry) {
        // 등기부가 없는 서버도 기록 조회는 한다. 쓰기는 requiredContractAddress 가 막는다
        if (!registry.isConfigured()) {
            return new SsafyStoDocumentClient(restClient(properties, properties.readTimeout()),
                    restClient(properties, properties.readTimeout()), properties.baseUrl(), null);
        }

        return new SsafyStoDocumentClient(
                restClient(properties, properties.readTimeout()),
                restClient(properties, registry.writeTimeout()),
                properties.baseUrl(),
                SsafyAgentRegistry.from(registry));
    }

    private static RestClient restClient(SsafyStoProperties properties, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
