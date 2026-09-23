package com.aiblockclock.domain.agent;

import com.aiblockclock.client.ai.IndexOutcome;
import com.aiblockclock.client.ai.IndexedAgent;
import com.aiblockclock.client.ai.RecommendClient;
import com.aiblockclock.client.ai.RecommendClientException;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.agent.Tier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

// 추천 엔진의 색인은 우리 DB 에서 파생된 캐시다. 밀어 넣지 못해도 등록은 성사된 것이므로
// 여기서 예외를 밖으로 내보내지 않는다. 빠진 것은 전체 다시 넣기로 메운다.
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentIndexService {
    // 실행 이력이 붙기 전까지 성공률은 갈리지 않는다. 추천 엔진의 기본값과 같은 값을 보낸다.
    private static final double UNMEASURED_SUCCESS_RATE = 1.0;

    private final RecommendClient recommendClient;
    private final AgentReader agentReader;

    public void index(AgentRegistration registered) {
        Agent agent = registered.agent();
        IndexedAgent record = toRecord(
                agent.getId(),
                agent.getOwner(),
                agent.getProfile().name(),
                agent.getProfile().summary(),
                registered.version().getTier());

        push("등록", () -> recommendClient.upsert(List.of(record)));
    }

    // 추천 엔진이 재기동하면 색인이 비어 있을 수 있고, 그때 등록 시점은 이미 지나갔다.
    // 우리가 올라올 때 한 번 채워 두는 것이 색인을 되살리는 유일한 길이다.
    // 추천은 없어도 서비스가 도는 기능이다. 무엇이 터지든 기동을 막지 않는다.
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        try {
            rebuild();
        } catch (RuntimeException exception) {
            log.warn("기동 시 추천 색인을 채우지 못했다: {}", exception.getMessage(), exception);
        }
    }

    public void rebuild() {
        List<IndexedAgent> records = agentReader.readPublic().stream()
                .map(agent -> toRecord(agent.id(), agent.owner(), agent.name(), agent.summary(), agent.tier()))
                .toList();

        push("전체 다시 넣기", () -> recommendClient.rebuild(records));
    }

    // 프롬프트 원문과 노드 구성은 담지 않는다. 마켓에 공개되는 값만 보낸다.
    private static IndexedAgent toRecord(
            AgentId id, UserId owner, String name, String summary, Tier tier) {
        return new IndexedAgent(
                id.value().toString(),
                name,
                summary,
                List.of(),
                "",
                String.valueOf(owner.value()),
                tier.level(),
                UNMEASURED_SUCCESS_RATE,
                true);
    }

    private static void push(String what, Push push) {
        try {
            IndexOutcome outcome = push.run();
            log.info("추천 색인 {} 완료: 반영 {}건, 색인 전체 {}건, 모델 {}",
                    what, outcome.indexed(), outcome.total(), outcome.modelVersion());
        } catch (RecommendClientException exception) {
            log.warn("추천 색인 {} 실패: {}", what, exception.getMessage(), exception);
        }
    }

    @FunctionalInterface
    private interface Push {
        IndexOutcome run();
    }
}
