package com.aiblockclock.domain.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPropertiesTest {

    private static final String ADMIN = "0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesTestConfiguration.class);

    @Test
    void 기본값은_관리자가_없다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            AdminProperties properties = context.getBean(AdminProperties.class);

            assertThat(properties.walletAddresses()).isEmpty();
            assertThat(properties.isAdmin(ADMIN)).isFalse();
        });
    }

    @Test
    void 쉼표로_여러_지갑을_적을_수_있고_대소문자를_가리지_않는다() {
        contextRunner.withPropertyValues(
                        "admin.wallet-addresses=" + ADMIN + ",0x2B5AD5c4795c026514f8317c7a215E218DcCD6cF")
                .run(context -> {
                    AdminProperties properties = context.getBean(AdminProperties.class);

                    assertThat(properties.isAdmin(ADMIN.toLowerCase())).isTrue();
                    assertThat(properties.isAdmin("0x2b5ad5c4795c026514f8317c7a215e218dccd6cf")).isTrue();
                    assertThat(properties.isAdmin("0x1111111111111111111111111111111111111111")).isFalse();
                });
    }

    @Test
    void 주소_형식이_틀리면_기동하지_않는다() {
        contextRunner.withPropertyValues("admin.wallet-addresses=0x123")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("관리자 지갑 주소 형식이 올바르지 않습니다."));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AdminProperties.class)
    static class PropertiesTestConfiguration {
    }
}
