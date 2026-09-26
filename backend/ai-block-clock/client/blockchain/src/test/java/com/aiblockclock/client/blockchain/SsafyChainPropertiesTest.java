package com.aiblockclock.client.blockchain;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.web3j.protocol.Web3j;

import static org.assertj.core.api.Assertions.assertThat;

class SsafyChainPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SsafyChainConfiguration.class)
            .withPropertyValues(
                    "ssafy.chain.rpc-url=https://rpc.ssafy-blockchain.com",
                    "ssafy.chain.connect-timeout=3s",
                    "ssafy.chain.read-timeout=10s"
            );

    @Test
    void RPC_주소와_제한_시간을_읽어_연결_객체를_만든다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            SsafyChainProperties properties = context.getBean(SsafyChainProperties.class);

            assertThat(properties.rpcUrl()).isEqualTo(URI.create("https://rpc.ssafy-blockchain.com"));
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(context).hasSingleBean(Web3j.class);
        });
    }

    @Test
    void HTTP가_아닌_RPC_주소면_기동하지_않는다() {
        contextRunner.withPropertyValues("ssafy.chain.rpc-url=wss://ws.ssafy-blockchain.com")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 제한_시간이_0이면_기동하지_않는다() {
        contextRunner.withPropertyValues("ssafy.chain.read-timeout=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 제한_시간이_음수면_기동하지_않는다() {
        contextRunner.withPropertyValues("ssafy.chain.connect-timeout=-1s")
                .run(context -> assertThat(context).hasFailed());
    }
}
