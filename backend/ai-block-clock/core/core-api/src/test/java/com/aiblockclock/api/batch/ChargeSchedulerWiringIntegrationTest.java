package com.aiblockclock.api.batch;

import com.aiblockclock.client.blockchain.SsafyChainClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

// 재확인을 켜면 충전 전용 TaskScheduler 가 생긴다. 충전 재확인이 제 스케줄러로 기동하는지 본다
@Tag("integration")
@SpringBootTest(properties = "payment.charge.polling-enabled=true")
class ChargeSchedulerWiringIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private SsafyChainClient ssafyChainClient;

    @Test
    void 재확인을_켜면_충전_전용_스케줄러로_기동한다() {
        assertThat(context.getBean(ChargeVerificationScheduler.class)).isNotNull();
        assertThat(context.getBean("chargeScheduler")).isInstanceOf(TaskScheduler.class);
    }
}
