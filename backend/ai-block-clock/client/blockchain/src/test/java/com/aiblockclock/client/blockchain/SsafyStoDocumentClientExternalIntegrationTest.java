package com.aiblockclock.client.blockchain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "SSAFY_STO_EXTERNAL_TEST", matches = "true")
class SsafyStoDocumentClientExternalIntegrationTest {
    // 읽기만 한다. 체인에 쓰는 테스트는 두지 않는다 — 한 번 적은 거래는 되돌릴 수 없다
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.config.name=blockchain")
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(SsafyStoConfiguration.class);

    @Test
    void 실제_토큰증권_API_에서_없는_문서는_빈_값으로_읽는다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            SsafyStoDocumentClient client = context.getBean(SsafyStoDocumentClient.class);
            String abcToken = context.getBean(SsafyTokenProperties.class).requiredContractAddress();

            assertThat(client.findDocument(abcToken, "no-such-document")).isEmpty();
        });
    }
}
