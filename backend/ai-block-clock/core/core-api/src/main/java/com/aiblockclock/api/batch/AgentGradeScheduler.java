package com.aiblockclock.api.batch;

import com.aiblockclock.domain.agent.AgentGradeService;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.agent.AgentIndexService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 등급 · 단가 재산정(PAY-022)을 정해진 때에 돌린다. 언제 도는지는 agent-grade.regrade.cron 이 정한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentGradeScheduler {
    private final AgentGradeService agentGradeService;
    private final AgentIndexService agentIndexService;

    // 에이전트마다 따로 트랜잭션을 연다. 하나가 실패해도 그 에이전트만 되돌리고 나머지는 계속 매긴다.
    // 실패한 에이전트는 로그에 남기고 다음 회차에 다시 매긴다. 매번 모든 에이전트를 재료에서 새로 매기므로 따로 쌓아 둘 것이 없다.
    // 추천 색인은 다 매긴 뒤 한 번에 다시 넣는다. 외부 호출이라 등급을 바꾸는 트랜잭션 밖에서 한다.
    @Scheduled(cron = "${agent-grade.regrade.cron}", zone = "${agent-grade.regrade.zone}")
    public void regradeAll() {
        List<AgentId> targets = agentGradeService.selectTargets();
        List<AgentId> failed = new ArrayList<>();

        for (AgentId agent : targets) {
            try {
                agentGradeService.regrade(agent);
            } catch (RuntimeException exception) {
                failed.add(agent);
                log.warn("등급 재산정 실패, 다음 회차에 다시 매긴다: agentId={}", agent.value(), exception);
            }
        }

        log.info("등급 재산정 끝: 대상 {}건, 실패 {}건 {}", targets.size(), failed.size(),
                failed.stream().map(AgentId::value).toList());

        agentIndexService.rebuild();
    }
}
