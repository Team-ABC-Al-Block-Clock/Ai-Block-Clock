package com.aiblockclock.client.blockchain;

import okhttp3.OkHttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SsafyChainProperties.class, SsafyChainSignerProperties.class})
public class SsafyChainConfiguration {
    // 대기 중인 거래를 주기적으로 한 번씩 묻기만 하므로 연결을 붙잡아 두는 WebSocket 이 아니라 HTTP 로 붙는다.
    // 끊김·재연결을 챙길 필요가 없고, 요청마다 제한 시간이 걸린다.
    @Bean(destroyMethod = "shutdown")
    public Web3j ssafyChainWeb3j(SsafyChainProperties properties) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.connectTimeout())
                .readTimeout(properties.readTimeout())
                .build();

        return Web3j.build(new HttpService(properties.rpcUrl().toString(), httpClient));
    }
}
