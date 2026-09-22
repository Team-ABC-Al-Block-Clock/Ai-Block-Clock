package com.aiblockclock.client.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AiProperties.class, RecommendProperties.class, RuntimeProperties.class})
public class AiClientConfiguration {
    @Bean
    public RecommendClient recommendClient(RecommendProperties properties, AiProperties shared) {
        return new RecommendClient(AiEndpoint.restClient(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout(), shared));
    }

    @Bean
    public AiRuntimeClient aiRuntimeClient(RuntimeProperties properties, AiProperties shared) {
        return new AiRuntimeClient(AiEndpoint.restClient(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout(), shared));
    }
}
