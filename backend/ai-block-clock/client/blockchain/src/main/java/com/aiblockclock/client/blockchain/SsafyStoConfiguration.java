package com.aiblockclock.client.blockchain;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SsafyStoProperties.class, SsafyTokenProperties.class})
public class SsafyStoConfiguration {
    // 애플리케이션은 온체인 상태를 조회만 한다. 배포·발행은 운영자가 스크립트로 수행한다.
    @Bean
    public RestClient ssafyStoQueryRestClient(SsafyStoProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
