package com.aiblockclock.client.blockchain;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SsafyAgentRegistryPropertiesTest {
    private static final String REGISTRY = "0x1111111111111111111111111111111111111111";
    private static final String TEST_PRIVATE_KEY = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SsafyStoConfiguration.class)
            .withPropertyValues(
                    "ssafy.sto.base-url=https://sto.ssafy.io",
                    "ssafy.sto.connect-timeout=3s",
                    "ssafy.sto.read-timeout=10s"
            );

    @Test
    void 등기부를_설정하지_않은_서버도_기동한다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SsafyAgentRegistryProperties.class).isConfigured()).isFalse();
            assertThat(context).hasSingleBean(SsafyStoDocumentClient.class);
        });
    }

    @Test
    void 주소_개인키_제한_시간이_모두_맞으면_기록을_쓰는_서버로_기동한다() {
        contextRunner.withPropertyValues(
                        "ssafy.sto.agent-registry.contract-address=" + REGISTRY,
                        "ssafy.sto.agent-registry.signer-private-key=" + TEST_PRIVATE_KEY,
                        "ssafy.sto.agent-registry.write-timeout=30s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SsafyAgentRegistryProperties properties = context.getBean(SsafyAgentRegistryProperties.class);
                    assertThat(properties.isConfigured()).isTrue();
                    assertThat(properties.writeTimeout()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void 주소만_있고_개인키가_없으면_기동하지_않는다() {
        contextRunner.withPropertyValues(
                        "ssafy.sto.agent-registry.contract-address=" + REGISTRY,
                        "ssafy.sto.agent-registry.write-timeout=30s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 개인키만_있고_주소가_없으면_기동하지_않는다() {
        contextRunner.withPropertyValues(
                        "ssafy.sto.agent-registry.signer-private-key=" + TEST_PRIVATE_KEY,
                        "ssafy.sto.agent-registry.write-timeout=30s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 쓰기_제한_시간이_없으면_기동하지_않는다() {
        contextRunner.withPropertyValues(
                        "ssafy.sto.agent-registry.contract-address=" + REGISTRY,
                        "ssafy.sto.agent-registry.signer-private-key=" + TEST_PRIVATE_KEY)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 개인키는_문자열로_찍혀도_가려진다() {
        SsafyAgentRegistryProperties properties =
                new SsafyAgentRegistryProperties(REGISTRY, TEST_PRIVATE_KEY, Duration.ofSeconds(30));

        assertThat(properties.toString()).doesNotContain(TEST_PRIVATE_KEY).contains("****");
    }
}
