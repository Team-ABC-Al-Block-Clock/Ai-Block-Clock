package com.aiblockclock.api.batch;

import com.aiblockclock.domain.agent.AgentProvenanceProperties;
import com.aiblockclock.domain.agent.AgentProvenanceService;
import com.aiblockclock.domain.agent.AgentVersionId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 공개할 때 쌓인 원본 기록을 주기마다 체인에 적는다. 대기 목록을 DB 에서 읽으므로 서버를 다시 띄워도 이어진다.
// 한 차례에 registration-batch-size 건까지 본다. 체인이 받아 주지 않은 건이 나오면 이번 차례는 거기서 멈춘다 —
// 체인에 닿지 못한 것이라면 나머지도 제한 시간만큼씩 기다리게 되기 때문이다. 멈춘 건은 뒤로 밀려 다음 차례에 다른 것부터 본다.
// 한 건의 내용을 읽다 깨진 것(예외)은 그 건만의 문제라 건너뛰고 나머지를 본다.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent-provenance", name = "registration-enabled", havingValue = "true")
public class AgentProvenanceScheduler {
    private final AgentProvenanceService agentProvenanceService;
    private final AgentProvenanceProperties agentProvenanceProperties;

    // 보내기를 켰는데 등기부(주소 · 키)가 없으면 한 건도 적을 수 없다. 조용히 쌓이게 두지 않고 기동을 멈춘다
    @EventListener(ApplicationReadyEvent.class)
    public void checkRegistry() {
        agentProvenanceService.ensureRegistrationReady();
    }

    @Scheduled(fixedDelayString = "${agent-provenance.registration-interval}",
            initialDelayString = "${agent-provenance.registration-interval}")
    public void registerPending() {
        for (AgentVersionId versionId
                : agentProvenanceService.readPendingVersionIds(agentProvenanceProperties.registrationBatchSize())) {
            try {
                if (!agentProvenanceService.register(versionId)) {
                    return;
                }
            } catch (RuntimeException exception) {
                log.error("원본 기록을 보내지 못했다. 다음 차례에 다시 본다: versionId={}", versionId.value(), exception);
            }
        }
    }
}
