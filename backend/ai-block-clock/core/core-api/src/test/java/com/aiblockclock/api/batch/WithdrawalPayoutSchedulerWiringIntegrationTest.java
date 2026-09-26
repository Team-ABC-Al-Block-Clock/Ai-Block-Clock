package com.aiblockclock.api.batch;

import com.aiblockclock.client.blockchain.SsafyChainClient;
import com.aiblockclock.client.blockchain.SsafyStoClient;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

// 지급을 켜면 TaskScheduler 가 하나 더 생긴다. 지급 · 실행 조회가 각자 제 스케줄러를 받는지 본다.
// 서명 키는 Hardhat 공개 테스트 계정이고, 보관 지갑을 그 주소로 맞춰 기동 검사를 지나게 한다.
@Tag("integration")
@SpringBootTest(properties = {
        "payment.withdrawal.payout.enabled=true",
        "payment.withdrawal.payout.poll-interval=1h",
        "payment.withdrawal.payout.hold-after=2h",
        "ssafy.chain.signer.private-key=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80",
        "payment.charge.custody-address=0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
})
class WithdrawalPayoutSchedulerWiringIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private SsafyChainClient ssafyChainClient;

    @MockitoBean
    private SsafyStoClient ssafyStoClient;

    @Test
    void 지급을_켜면_지급_전용_스케줄러로_기동한다() {
        assertThat(context.getBean(WithdrawalPayoutScheduler.class)).isNotNull();
        assertThat(context.getBean("payoutScheduler")).isNotSameAs(context.getBean("executionScheduler"));
        assertThat(context.getBean(ExecutionProgressScheduler.class)).isNotNull();
    }
}
