package com.aiblockclock.support.blockchain;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SsafyStoProperties.class)
public class SsafyStoConfiguration {
    @Bean
    public RestClient ssafyStoQueryRestClient(SsafyStoProperties properties) {
        return createRestClient(properties, properties.readTimeout());
    }

    @Bean
    public RestClient ssafyStoTransactionRestClient(SsafyStoProperties properties) {
        return createRestClient(properties, properties.transactionTimeout());
    }

    private RestClient createRestClient(SsafyStoProperties properties, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
