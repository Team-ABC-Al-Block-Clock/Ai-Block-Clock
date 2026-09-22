package com.aiblockclock.domain.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 추천 엔진이 재기동하면 색인이 비어 있을 수 있고, 그때 등록 시점은 이미 지나갔다.
// 우리가 올라올 때 한 번 채워 두는 것이 색인을 되살리는 유일한 길이다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentIndexRebuilder {
    private final AgentIndexer agentIndexer;

    // 추천은 없어도 서비스가 도는 기능이다. 무엇이 터지든 기동을 막지 않는다.
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        try {
            agentIndexer.rebuild();
        } catch (RuntimeException exception) {
            log.warn("기동 시 추천 색인을 채우지 못했다: {}", exception.getMessage(), exception);
        }
    }
}
