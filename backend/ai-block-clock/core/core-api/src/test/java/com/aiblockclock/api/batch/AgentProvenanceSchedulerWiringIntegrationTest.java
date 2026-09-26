package com.aiblockclock.api.batch;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// 보내기를 켜고 등기부를 설정하면 주기 작업이 뜨는지 본다. 간격을 길게 둬 테스트 중에는 한 번도 돌지 않는다
@Tag("integration")
@SpringBootTest(properties = {
        "agent-provenance.registration-enabled=true",
        "agent-provenance.registration-interval=1h",
        "ssafy.sto.agent-registry.contract-address=0x1111111111111111111111111111111111111111",
        // 널리 알려진 테스트 전용 키다(Hardhat 0번 계정). 실제 지갑이 아니다
        "ssafy.sto.agent-registry.signer-private-key=ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
})
class AgentProvenanceSchedulerWiringIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void 보내기를_켜면_원본_기록_주기_작업으로_기동한다() {
        assertThat(context.getBean(AgentProvenanceScheduler.class)).isNotNull();
    }
}
